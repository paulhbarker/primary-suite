package com.primarysuite.app.games.luckytap

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.primarysuite.app.R
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.nfc.NfcScanResult
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.nfc.NfcWriteResult
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.ui.UiEvent
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeNfcTag
import com.primarysuite.app.testing.FakeStrings
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Lucky Tap scanning behaviour. Ported from the phase 2 parity spec for the
 * XML screen; tags now arrive through [NfcHub] instead of the activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LuckyTapViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val strings = FakeStrings()
    private val wifi = FakeWifiConnector()
    private val nfc = NfcHub()
    private val socket = FakeGameConnection()
    private var socketCanConnect: (() -> Boolean)? = null

    private fun create() = LuckyTapViewModel(strings, wifi, nfc, { _, canConnect ->
        socketCanConnect = canConnect
        socket
    }, ioDispatcher = main.dispatcher)

    // Lazy so it's created after MainDispatcherRule has replaced Dispatchers.Main.
    private val vm: LuckyTapViewModel by lazy { create() }

    private val events = mutableListOf<UiEvent>()

    private fun s(id: Int, vararg args: Any) = strings.get(id, *args)

    private fun TestScope.collectEvents() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvents.toList(events) }
    }

    /** NFC on, WiFi on the target network, socket open. */
    private fun TestScope.goOnline() {
        nfc.setState(NfcState.AVAILABLE)
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        vm
        runCurrent()
    }

    /** Taps a card and lets the read finish. */
    private fun TestScope.tap(tag: FakeNfcTag) {
        vm
        runCurrent() // let the view model subscribe to tags (Main.immediate does this at once in the app)
        nfc.dispatch(tag)
        runCurrent()
    }

    private fun toasts() = events.filterIsInstance<UiEvent.ShowToast>().map { it.message }

    private fun sentTypes() = socket.sent.map { it.type }

    // --- Connections ---

    @Test
    fun `starts idle, asking for nfc`() = runTest(main.dispatcher) {
        vm
        runCurrent()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertFalse(vm.areEssentialConnectionsActive.value)
        assertFalse(vm.isWriteButtonEnabled.value)
        assertEquals(s(R.string.toast_enable_nfc), vm.connectionStatusText.value)
    }

    @Test
    fun `with nfc on it waits for wifi and the server`() = runTest(main.dispatcher) {
        nfc.setState(NfcState.AVAILABLE)
        vm
        runCurrent()

        assertEquals(s(R.string.status_waiting_for_connections), vm.connectionStatusText.value)
    }

    @Test
    fun `socket may only connect while wifi is up`() = runTest(main.dispatcher) {
        vm
        runCurrent()
        assertFalse(socketCanConnect!!())

        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()

        assertTrue(socketCanConnect!!())
    }

    @Test
    fun `wifi coming up opens the socket`() = runTest(main.dispatcher) {
        vm
        runCurrent()
        assertEquals(0, socket.connectCalls)

        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()

        assertEquals(1, socket.connectCalls)
    }

    @Test
    fun `opening the screen on wifi connects straight away`() = runTest(main.dispatcher) {
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        vm
        runCurrent()

        assertEquals(1, socket.connectCalls)
    }

    @Test
    fun `active only when nfc, wifi and socket are all up`() = runTest(main.dispatcher) {
        goOnline()
        assertTrue(vm.areEssentialConnectionsActive.value)

        nfc.setState(NfcState.DISABLED); runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
        nfc.setState(NfcState.AVAILABLE); runCurrent()

        wifi.connectionState.value = WifiConnectionState.CONNECTING; runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
        wifi.connectionState.value = WifiConnectionState.CONNECTED; runCurrent()

        socket.connectionState.value = WebSocketConnectionState.CONNECTING; runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
    }

    @Test
    fun `status text shows idle once online`() = runTest(main.dispatcher) {
        goOnline()
        assertEquals(s(R.string.status_idle), vm.connectionStatusText.value)
    }

    @Test
    fun `socket giving up shows a toast`() = runTest(main.dispatcher) {
        collectEvents()
        runCurrent()

        socket.events.emit(SocketEvent.GaveUp(10))
        runCurrent()

        assertEquals(listOf(s(R.string.toast_ws_gave_up, 10)), toasts())
    }

    @Test
    fun `closing the screen shuts the socket`() = runTest(main.dispatcher) {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { create() } })[LuckyTapViewModel::class]

        store.clear()

        assertTrue(socket.shutdownCalled)
    }

    // --- Scanning ---

    @Test
    fun `read mode sends card-scanned and shows the number, then resumes scanning`() = runTest(main.dispatcher) {
        goOnline()

        vm.onReadButtonModeSelected()
        assertEquals(AppUiState.SCANNING, vm.uiState.value)
        assertEquals(NfcOperationMode.READ, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_scanning_read), vm.nfcStatusMessage.value)

        tap(FakeNfcTag.card(42))

        assertEquals(GameMessage("card-scanned", JSONObject().put("number", 42)), socket.sent.single())
        assertEquals(AppUiState.SUCCESS_DISPLAY, vm.uiState.value)
        assertEquals("42", vm.numberForSuccessDisplay.value)

        advanceTimeBy(LuckyTapViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        assertEquals(AppUiState.SCANNING, vm.uiState.value)
        assertEquals(NfcOperationMode.READ, vm.currentNfcMode.value)
        assertNull(vm.numberForSuccessDisplay.value)
    }

    @Test
    fun `check-in and check-out send their own message types`() = runTest(main.dispatcher) {
        goOnline()

        vm.onCheckInButtonModeSelected()
        assertEquals(s(R.string.status_scanning_check_in), vm.nfcStatusMessage.value)
        tap(FakeNfcTag.card(7))
        advanceTimeBy(LuckyTapViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        vm.onCheckOutButtonModeSelected()
        assertEquals(s(R.string.status_scanning_check_out), vm.nfcStatusMessage.value)
        tap(FakeNfcTag.card(8))

        assertEquals(listOf("nfc-check-in", "nfc-check-out"), sentTypes())
        assertEquals(listOf(7, 8), socket.sent.map { it.payload.getInt("number") })
    }

    @Test
    fun `cards tapped during the success display are ignored`() = runTest(main.dispatcher) {
        goOnline()
        vm.onCheckInButtonModeSelected()
        tap(FakeNfcTag.card(7))

        tap(FakeNfcTag.card(8))

        assertEquals(listOf(7), socket.sent.map { it.payload.getInt("number") })
    }

    @Test
    fun `a failed tag read stops scanning and explains why`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()
        vm.onReadButtonModeSelected()

        tap(FakeNfcTag(NfcScanResult(success = false, error = "Tag lost")))

        assertTrue(socket.sent.isEmpty())
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_read_error, "Tag lost"), vm.nfcStatusMessage.value)
        assertEquals(listOf(s(R.string.toast_read_error, "Tag lost")), toasts())
        assertTrue((events.single() as UiEvent.ShowToast).longDuration)
    }

    @Test
    fun `a tag without text reports no ndef data`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        tap(FakeNfcTag(NfcScanResult(success = true, data = null)))

        assertEquals(s(R.string.status_read_error, s(R.string.status_no_ndef)), vm.nfcStatusMessage.value)
    }

    @Test
    fun `a scan the socket rejects is not shown as a success`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()
        socket.acceptSends = false
        vm.onCheckInButtonModeSelected()

        tap(FakeNfcTag.card(42))

        assertNull(vm.numberForSuccessDisplay.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(listOf(s(R.string.toast_read_error_short)), toasts())
    }

    @Test
    fun `losing a connection mid-scan returns to idle`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        wifi.connectionState.value = WifiConnectionState.DISCONNECTED
        runCurrent()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_connection_lost), vm.nfcStatusMessage.value)
    }

    @Test
    fun `losing a connection during the success display does not resume scanning`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()
        tap(FakeNfcTag.card(5))

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        advanceTimeBy(LuckyTapViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertNull(vm.numberForSuccessDisplay.value)
    }

    @Test
    fun `back cancels a scan`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        assertTrue(vm.onBackButtonPressed())

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_scan_cancelled), vm.nfcStatusMessage.value)
    }

    @Test
    fun `back while idle is left to navigation`() = runTest(main.dispatcher) {
        goOnline()
        assertFalse(vm.onBackButtonPressed())
        assertNull(vm.nfcStatusMessage.value)
    }

    @Test
    fun `a card read that finishes after cancelling is dropped`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()
        runCurrent()
        nfc.dispatch(FakeNfcTag.card(9)) // read starts...
        vm.onBackButtonPressed() // ...user cancels before it completes
        runCurrent()

        assertTrue(socket.sent.isEmpty())
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
    }

    @Test
    fun `a card tapped while idle explains without toasting`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()

        tap(FakeNfcTag.card(3))

        assertEquals(s(R.string.nfc_not_active_mode), vm.nfcStatusMessage.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertTrue(toasts().isEmpty())
        assertTrue(socket.sent.isEmpty())
    }

    // --- Writing ---

    @Test
    fun `write is enabled only for card numbers 1 to 100`() = runTest(main.dispatcher) {
        fun enabledFor(text: String): Boolean {
            vm.onWriteInputChange(text)
            runCurrent()
            return vm.isWriteButtonEnabled.value
        }
        assertTrue(enabledFor("42"))
        assertFalse(enabledFor("101"))
        assertTrue(enabledFor("1"))
        assertFalse(enabledFor("0"))
        assertFalse(enabledFor(""))
    }

    @Test
    fun `the write field keeps at most three digits`() = runTest(main.dispatcher) {
        vm.onWriteInputChange("4a2-")
        assertEquals("42", vm.writeInput.value)
        vm.onWriteInputChange("12345")
        assertEquals("123", vm.writeInput.value)
    }

    @Test
    fun `write mode needs a valid number`() = runTest(main.dispatcher) {
        vm.onWriteInputChange("500")
        vm.onWriteButtonModeSelected()
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_invalid_number), vm.nfcStatusMessage.value)

        vm.onWriteInputChange("42")
        vm.onWriteButtonModeSelected()
        assertEquals(NfcOperationMode.WRITE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_ready_write, "42"), vm.nfcStatusMessage.value)
    }

    @Test
    fun `writing does not need the server`() = runTest(main.dispatcher) {
        nfc.setState(NfcState.AVAILABLE)
        vm.onWriteInputChange("42")
        vm.onWriteButtonModeSelected()
        val blank = FakeNfcTag()

        tap(blank)

        assertEquals(listOf("42"), blank.writes)
    }

    @Test
    fun `a successful write confirms and returns to idle`() = runTest(main.dispatcher) {
        collectEvents()
        vm.onWriteInputChange("42")
        vm.onWriteButtonModeSelected()
        val blank = FakeNfcTag()

        tap(blank)

        assertEquals(listOf("42"), blank.writes)
        assertEquals(s(R.string.status_write_success, "42"), vm.nfcStatusMessage.value)
        assertEquals(listOf(s(R.string.toast_wrote_tag, "42")), toasts())
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
    }

    @Test
    fun `a failed write reports the error`() = runTest(main.dispatcher) {
        collectEvents()
        vm.onWriteInputChange("42")
        vm.onWriteButtonModeSelected()

        tap(FakeNfcTag(writeResult = NfcWriteResult(success = false, error = "Tag too small.")))

        assertEquals(s(R.string.status_write_error, "Tag too small."), vm.nfcStatusMessage.value)
        assertEquals(listOf(s(R.string.toast_write_error, "Tag too small.")), toasts())
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
    }

    @Test
    fun `choosing write stops a scan in progress`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        vm.onWriteInputChange("12")
        vm.onWriteButtonModeSelected()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.WRITE, vm.currentNfcMode.value)
    }

    // --- Admin ---

    @Test
    fun `reset and clear send admin messages when online`() = runTest(main.dispatcher) {
        goOnline()

        vm.onResetGameButtonPressed()
        assertEquals(s(R.string.status_reset_sent), vm.nfcStatusMessage.value)
        vm.onClearScansButtonPressed()
        assertEquals(s(R.string.status_clear_sent), vm.nfcStatusMessage.value)

        assertEquals(listOf("admin-reset-game", "admin-clear-scans"), sentTypes())
        assertTrue(socket.sent.all { it.payload.length() == 0 })
    }

    @Test
    fun `reset and clear refuse when offline`() = runTest(main.dispatcher) {
        collectEvents()
        runCurrent()

        vm.onResetGameButtonPressed()
        assertEquals(s(R.string.status_reset_no_connection), vm.nfcStatusMessage.value)
        vm.onClearScansButtonPressed()
        assertEquals(s(R.string.status_clear_no_connection), vm.nfcStatusMessage.value)
        runCurrent()

        assertTrue(socket.sent.isEmpty())
        assertEquals(List(2) { s(R.string.toast_no_connection) }, toasts())
    }
}
