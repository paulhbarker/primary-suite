package com.primarysuite.app.games.luckytap

import com.primarysuite.app.R
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcScanResult
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.ui.UiEvent
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeStrings
import com.primarysuite.app.testing.InMemoryConnectionSettings
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
 * The current Lucky Tap scanning behaviour. These tests are the parity spec for
 * the Compose rewrite in phase 4.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcAppViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val strings = FakeStrings()
    private val settings = InMemoryConnectionSettings()
    private val socket = FakeGameConnection()
    private var socketScopeCanConnect: (() -> Boolean)? = null

    // Lazy so it's created after MainDispatcherRule has replaced Dispatchers.Main.
    private val vm by lazy {
        NfcAppViewModel(strings, settings) { _, canConnect ->
            socketScopeCanConnect = canConnect
            socket
        }
    }

    private val events = mutableListOf<UiEvent>()

    private fun s(id: Int, vararg args: Any) = strings.get(id, *args)

    private fun TestScope.collectEvents() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvents.toList(events) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.connectionStatusText.collect {} }
    }

    /** NFC on, WiFi on the target network, socket open. */
    private fun TestScope.goOnline() {
        vm.setNfcState(NfcState.AVAILABLE)
        vm.setWifiConnected(true)
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        runCurrent()
    }

    private fun toasts() = events.filterIsInstance<UiEvent.ShowToast>().map { it.message }

    private fun sentTypes() = socket.sent.map { it.type }

    // --- Connections ---

    @Test
    fun `starts idle, asking for nfc`() = runTest(main.dispatcher) {
        collectEvents()
        runCurrent()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertFalse(vm.areEssentialConnectionsActive.value)
        assertFalse(vm.isWriteButtonEnabled.value)
        assertEquals(s(R.string.toast_enable_nfc), vm.connectionStatusText.value)
    }

    @Test
    fun `with nfc on it waits for wifi and the server`() = runTest(main.dispatcher) {
        collectEvents()
        vm.setNfcState(NfcState.AVAILABLE)
        runCurrent()

        assertEquals(s(R.string.status_waiting_for_connections), vm.connectionStatusText.value)
    }

    @Test
    fun `socket may only connect while wifi is up`() = runTest(main.dispatcher) {
        vm.setWifiConnected(false)
        assertFalse(socketScopeCanConnect!!())

        vm.setWifiConnected(true)

        assertTrue(socketScopeCanConnect!!())
    }

    @Test
    fun `wifi coming up opens the socket`() = runTest(main.dispatcher) {
        runCurrent()
        assertEquals(0, socket.connectCalls)

        vm.setWifiConnected(true)
        runCurrent()

        assertEquals(1, socket.connectCalls)
    }

    @Test
    fun `active only when nfc, wifi and socket are all up`() = runTest(main.dispatcher) {
        goOnline()
        assertTrue(vm.areEssentialConnectionsActive.value)

        vm.setNfcState(NfcState.DISABLED); runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
        vm.setNfcState(NfcState.AVAILABLE); runCurrent()

        vm.setWifiConnected(false); runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
        vm.setWifiConnected(true); runCurrent()

        socket.connectionState.value = WebSocketConnectionState.CONNECTING; runCurrent()
        assertFalse(vm.areEssentialConnectionsActive.value)
    }

    @Test
    fun `status text asks for nfc first, then shows idle once online`() = runTest(main.dispatcher) {
        collectEvents()
        vm.setNfcState(NfcState.DISABLED); runCurrent()
        assertEquals(s(R.string.toast_enable_nfc), vm.connectionStatusText.value)

        goOnline()
        assertEquals(s(R.string.status_idle), vm.connectionStatusText.value)
    }

    @Test
    fun `changed settings reconnect the socket while on wifi`() = runTest(main.dispatcher) {
        vm.setWifiConnected(true); runCurrent()
        val before = socket.connectCalls

        vm.onPreferencesChanged()

        assertEquals(before + 1, socket.connectCalls)
    }

    @Test
    fun `changed settings do nothing while off wifi`() = runTest(main.dispatcher) {
        runCurrent()
        vm.onPreferencesChanged()
        assertEquals(0, socket.connectCalls)
    }

    @Test
    fun `exposes the effective wifi credentials`() {
        settings.wifiSsidOverride = "Backstage"
        assertEquals("Backstage", vm.getEffectiveWifiSsidForActivity())
        assertEquals("secret", vm.getEffectiveWifiPasswordForActivity())
    }

    @Test
    fun `socket giving up shows a toast`() = runTest(main.dispatcher) {
        collectEvents()
        runCurrent()

        socket.events.emit(SocketEvent.GaveUp(10))
        runCurrent()

        assertEquals(listOf(s(R.string.toast_ws_gave_up, 10)), toasts())
    }

    // --- Scanning ---

    @Test
    fun `read mode sends card-scanned and shows the number, then resumes scanning`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()

        vm.onReadButtonModeSelected()
        assertEquals(AppUiState.SCANNING, vm.uiState.value)
        assertEquals(NfcOperationMode.READ, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_scanning_read), vm.nfcStatusMessage.value)

        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = "42"))

        assertEquals(GameMessage("card-scanned", JSONObject().put("number", 42)), socket.sent.single())
        assertEquals(AppUiState.SUCCESS_DISPLAY, vm.uiState.value)
        assertEquals("42", vm.numberForSuccessDisplay.value)

        advanceTimeBy(NfcAppViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        assertEquals(AppUiState.SCANNING, vm.uiState.value)
        assertEquals(NfcOperationMode.READ, vm.currentNfcMode.value)
        assertNull(vm.numberForSuccessDisplay.value)
    }

    @Test
    fun `check-in and check-out send their own message types`() = runTest(main.dispatcher) {
        goOnline()

        vm.onCheckInButtonModeSelected()
        assertEquals(s(R.string.status_scanning_check_in), vm.nfcStatusMessage.value)
        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = "7"))
        advanceTimeBy(NfcAppViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        vm.onCheckOutButtonModeSelected()
        assertEquals(s(R.string.status_scanning_check_out), vm.nfcStatusMessage.value)
        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = "8"))

        assertEquals(listOf("nfc-check-in", "nfc-check-out"), sentTypes())
        assertEquals(listOf(7, 8), socket.sent.map { it.payload.getInt("number") })
    }

    @Test
    fun `a failed tag read stops scanning and explains why`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()
        vm.onReadButtonModeSelected()

        vm.onNfcTagScannedForRead(NfcScanResult(success = false, error = "Tag lost"))
        runCurrent()

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

        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = null))

        assertEquals(s(R.string.status_read_error, s(R.string.status_no_ndef)), vm.nfcStatusMessage.value)
    }

    @Test
    fun `a scan the socket rejects is not shown as a success`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()
        socket.acceptSends = false
        vm.onCheckInButtonModeSelected()

        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = "42"))
        runCurrent()

        assertNull(vm.numberForSuccessDisplay.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(listOf(s(R.string.toast_read_error_short)), toasts())
    }

    @Test
    fun `losing a connection mid-scan returns to idle`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        vm.setWifiConnected(false)
        runCurrent()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_connection_lost), vm.nfcStatusMessage.value)
    }

    @Test
    fun `losing a connection during the success display does not resume scanning`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()
        vm.onNfcTagScannedForRead(NfcScanResult(success = true, data = "5"))

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        advanceTimeBy(NfcAppViewModel.SUCCESS_DISPLAY_DURATION_MS + 1)

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
    }

    @Test
    fun `back cancels a scan`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        vm.onBackButtonPressed()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_scan_cancelled), vm.nfcStatusMessage.value)
    }

    @Test
    fun `back while idle changes nothing`() = runTest(main.dispatcher) {
        goOnline()
        vm.onBackButtonPressed()
        assertNull(vm.nfcStatusMessage.value)
    }

    @Test
    fun `a scan error while scanning toasts and keeps scanning`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()
        vm.onReadButtonModeSelected()

        vm.onNfcTagScanFailed("No tag in intent")
        runCurrent()

        assertEquals(AppUiState.SCANNING, vm.uiState.value)
        assertEquals(listOf("No tag in intent"), toasts())
    }

    @Test
    fun `a tag tapped while idle shows the reason without toasting`() = runTest(main.dispatcher) {
        collectEvents()
        goOnline()

        vm.onNfcTagScanFailed("Not in a scanning mode")
        runCurrent()

        assertEquals("Not in a scanning mode", vm.nfcStatusMessage.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertTrue(toasts().isEmpty())
    }

    @Test
    fun `reset to idle clears the mode`() = runTest(main.dispatcher) {
        goOnline()
        vm.onReadButtonModeSelected()

        vm.resetToIdle()

        assertEquals(AppUiState.NORMAL, vm.uiState.value)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_idle), vm.nfcStatusMessage.value)
    }

    // --- Writing ---

    @Test
    fun `write is enabled only for card numbers 1 to 100`() {
        vm.validateInputForWrite("42"); assertTrue(vm.isWriteButtonEnabled.value)
        vm.validateInputForWrite("101"); assertFalse(vm.isWriteButtonEnabled.value)
        vm.validateInputForWrite("1"); assertTrue(vm.isWriteButtonEnabled.value)
        vm.validateInputForWrite(""); assertFalse(vm.isWriteButtonEnabled.value)
    }

    @Test
    fun `write mode needs a valid number`() {
        vm.onWriteButtonModeSelected("500", currentInputIsValid = false)
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_invalid_number), vm.nfcStatusMessage.value)

        vm.onWriteButtonModeSelected("42", currentInputIsValid = true)
        assertEquals(NfcOperationMode.WRITE, vm.currentNfcMode.value)
        assertEquals(s(R.string.status_ready_write, "42"), vm.nfcStatusMessage.value)
    }

    @Test
    fun `a successful write confirms and returns to idle`() = runTest(main.dispatcher) {
        collectEvents()
        vm.onWriteButtonModeSelected("42", currentInputIsValid = true)

        vm.onNfcTagScannedForWrite("42", success = true)
        runCurrent()

        assertEquals(s(R.string.status_write_success, "42"), vm.nfcStatusMessage.value)
        assertEquals(listOf(s(R.string.toast_wrote_tag, "42")), toasts())
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
        assertEquals(AppUiState.NORMAL, vm.uiState.value)
    }

    @Test
    fun `a failed write reports the error`() = runTest(main.dispatcher) {
        collectEvents()
        vm.onWriteButtonModeSelected("42", currentInputIsValid = true)

        vm.onNfcTagScannedForWrite("42", success = false, errorMessage = "Tag too small.")
        runCurrent()

        assertEquals(s(R.string.status_write_error, "Tag too small."), vm.nfcStatusMessage.value)
        assertEquals(listOf(s(R.string.toast_write_error, "Tag too small.")), toasts())
        assertEquals(NfcOperationMode.NONE, vm.currentNfcMode.value)
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
