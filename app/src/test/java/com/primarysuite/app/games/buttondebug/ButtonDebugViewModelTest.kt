package com.primarysuite.app.games.buttondebug

import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.testing.MainDispatcherRule
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ButtonDebugViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private val store = InMemoryButtonColorStore()
    private var now = 10_000.0
    private val vm by lazy {
        ButtonDebugViewModel(wifi, { _, _ -> socket }, store, clockMs = { now }, workDispatcher = main.dispatcher)
    }
    private val errors = mutableListOf<String>()

    /** Runs a test, then drops the socket so the ping loop ends (it would run forever in virtual time). */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        try {
            body()
        } finally {
            socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
            runCurrent()
        }
    }

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.toList(errors) }
        runCurrent()
    }

    private suspend fun TestScope.connect() {
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        runCurrent()
    }

    private suspend fun TestScope.receive(type: String, payload: org.json.JSONObject) {
        socket.events.emit(SocketEvent.Received(GameMessage(type, payload)))
        runCurrent()
    }

    private suspend fun TestScope.sendSnapshot(s: DebugSnapshot = snapshot()) = receive(ButtonDebugProtocol.SNAPSHOT, s.toJson())

    /** What the user sent, leaving out the pings. */
    private val actions get() = socket.sent.filter { it.type != ButtonDebugProtocol.PING }

    @Test
    fun `slug is the web page's socket path`() {
        assertEquals("button-debug", ButtonDebugViewModel.GAME_SLUG)
    }

    @Test
    fun `connects when wifi comes up`() = vmTest {
        start()
        assertEquals(0, socket.connectCalls)
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()
        assertEquals(1, socket.connectCalls)
    }

    @Test
    fun `starts with the default colours plus the saved picks`() = vmTest {
        store.saved = mapOf(1 to ButtonColor.RED)
        start()
        assertEquals(ButtonColor.DEFAULTS + (1 to ButtonColor.RED), vm.ui.value.colors)
    }

    @Test
    fun `follows the snapshot, then status, edges and presses`() = vmTest {
        start()
        connect()
        assertTrue(vm.ui.value.connected)
        assertNull(vm.ui.value.session.info)
        assertTrue("nothing is sent but pings", actions.isEmpty())

        sendSnapshot(snapshot(events = Tap.down(0, seq = 1) + Tap.up(300_000), presses = listOf(press(id = 4, seq = 1))))
        val s = vm.ui.value.session
        assertTrue(s.hasBox)
        assertEquals(1, s.statsFor(0).presses)
        assertFalse(s.statsFor(0).live)
        assertEquals(1, s.statsFor(0).delivered)

        receive(ButtonDebugProtocol.STATUS, statusJson(quietMs = 30.0, enabled = listOf(true, false), activeGames = listOf("lucky-tap")))
        assertEquals(30.0, vm.ui.value.session.status!!.quietMs, 0.0)
        assertEquals(listOf(3), vm.ui.value.session.disabledIds)
        assertEquals(listOf("lucky-tap"), vm.ui.value.session.activeGames)

        Tap.down(1_000_000, seq = 2).forEach { receive(ButtonDebugProtocol.EDGE, it.toJson()) }
        receive(ButtonDebugProtocol.PRESS, press(id = 4, seq = 2).toJson())
        val live = vm.ui.value.session.statsFor(0)
        assertEquals(2, live.presses)
        assertTrue(live.live)
        assertEquals(2, live.delivered)

        receive(ButtonDebugProtocol.PRESS, press(id = 9, seq = 1, source = "wifi", rssi = -55).toJson())
        assertEquals(-55, vm.ui.value.session.wifi[9]!!.rssi)
    }

    @Test
    fun `a burst of edges is all taken in`() = vmTest {
        start()
        connect()
        sendSnapshot()
        repeat(300) { n -> receive(ButtonDebugProtocol.EDGE, edge(tsUs = n * 10L).toJson()) }
        assertEquals(300, vm.ui.value.session.statsFor(0).edges)
        assertEquals(200, vm.ui.value.session.log.size)
    }

    @Test
    fun `ignores unknown messages and edges without a button`() = vmTest {
        start()
        connect()
        sendSnapshot()
        val before = vm.ui.value.session
        receive("button-debug-from-the-future", org.json.JSONObject())
        receive(ButtonDebugProtocol.EDGE, org.json.JSONObject())
        assertEquals(before, vm.ui.value.session)
    }

    @Test
    fun `a reset snapshot starts the counters again`() = vmTest {
        start()
        connect()
        sendSnapshot(snapshot(events = Tap.down(0)))
        assertEquals(1, vm.ui.value.session.statsFor(0).presses)
        sendSnapshot()
        assertEquals(0, vm.ui.value.session.statsFor(0).presses)
        assertTrue(vm.ui.value.session.log.isEmpty())
    }

    @Test
    fun `pings on connect and every two seconds, and stops when the socket goes`() = vmTest {
        start()
        connect()
        assertEquals(1, socket.sent.count { it.type == "button-debug-ping" })
        assertEquals(10_000.0, socket.sent.last().payload.getDouble("t"), 0.0)

        now = 12_000.0
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, socket.sent.size)
        assertEquals(12_000.0, socket.sent.last().payload.getDouble("t"), 0.0)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, socket.sent.size)

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, socket.sent.size)

        // Back again: pings straight away.
        connect()
        assertEquals(3, socket.sent.size)
    }

    @Test
    fun `a pong gives the round trip, shown only while connected`() = vmTest {
        start()
        connect()
        assertNull(vm.ui.value.rttMs)
        assertFalse(vm.ui.value.showRtt)

        now = 10_042.5
        receive(ButtonDebugProtocol.PONG, org.json.JSONObject().put("t", 10_000.0))
        assertEquals(42.5, vm.ui.value.rttMs!!, 1e-9)
        assertTrue(vm.ui.value.showRtt)

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.showRtt)
    }

    @Test
    fun `a pong without a time is ignored, and a snapshot keeps the round trip`() = vmTest {
        start()
        connect()
        now = 10_010.0
        receive(ButtonDebugProtocol.PONG, org.json.JSONObject())
        assertNull(vm.ui.value.rttMs)
        receive(ButtonDebugProtocol.PONG, org.json.JSONObject().put("t", 10_000.0))
        sendSnapshot()
        assertEquals(10.0, vm.ui.value.rttMs!!, 1e-9)
    }

    @Test
    fun `failing pings are not reported`() = vmTest {
        start()
        socket.acceptSends = false
        connect()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `every LED mode is sent`() = vmTest {
        start()
        connect()
        LedMode.entries.forEach { vm.setLedMode(it) }
        assertEquals(LedMode.entries.map { ButtonDebugCommands.ledMode(it) }, actions)
    }

    @Test
    fun `chase, enable all and reset send one message each`() = vmTest {
        start()
        connect()
        vm.chase()
        vm.enableAll()
        vm.resetStats()
        assertEquals(listOf(ButtonDebugCommands.chase(), ButtonDebugCommands.enableAll(), ButtonDebugCommands.reset()), actions)
    }

    @Test
    fun `reset waits for the server's snapshot rather than clearing locally`() = vmTest {
        start()
        connect()
        sendSnapshot(snapshot(events = Tap.down(0)))
        vm.resetStats()
        assertEquals(1, vm.ui.value.session.statsFor(0).presses)
    }

    @Test
    fun `debounce is sent when in range`() = vmTest {
        start()
        connect()
        vm.setDebounce(30.0)
        vm.setDebounce(0.0)
        vm.setDebounce(500.0)
        vm.setDebounce(12.5)
        assertEquals(listOf(30.0, 0.0, 500.0, 12.5), actions.map { it.payload.getDouble("ms") })
        assertTrue(actions.all { it.type == "button-debug-debounce" })
    }

    @Test
    fun `debounce out of range is not sent`() = vmTest {
        start()
        connect()
        vm.setDebounce(-1.0)
        vm.setDebounce(501.0)
        vm.setDebounce(Double.NaN)
        assertTrue(actions.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `a colour pick shows at once and is saved with the others`() = vmTest {
        start()
        vm.setColor(2, ButtonColor.RED)
        assertEquals(ButtonColor.RED, vm.ui.value.colors[2])
        assertEquals(ButtonColor.GREEN, vm.ui.value.colors[1])
        assertEquals(1, store.saves)
        assertEquals(ButtonColor.DEFAULTS + (2 to ButtonColor.RED), store.saved)
        assertTrue("a colour needs no socket", socket.sent.isEmpty())
    }

    @Test
    fun `colours are remembered by a new view model`() = vmTest {
        start()
        vm.setColor(3, ButtonColor.BLUE)
        val again = ButtonDebugViewModel(wifi, { _, _ -> FakeGameConnection() }, store, workDispatcher = main.dispatcher)
        assertEquals(ButtonColor.BLUE, again.ui.value.colors[3])
    }

    @Test
    fun `failed sends report an error`() = vmTest {
        start()
        connect()
        socket.acceptSends = false
        vm.setLedMode(LedMode.ON)
        vm.chase()
        vm.enableAll()
        vm.setDebounce(20.0)
        vm.resetStats()
        assertEquals(5, errors.size)
    }

    @Test
    fun `losing the socket shows offline but keeps what was seen`() = vmTest {
        start()
        connect()
        sendSnapshot(snapshot(events = Tap.down(0)))
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.connected)
        assertEquals(1, vm.ui.value.session.statsFor(0).presses)
    }

    @Test
    fun `giving up on the server is reported`() = vmTest {
        start()
        socket.events.emit(SocketEvent.GaveUp(10))
        runCurrent()
        assertEquals(1, errors.size)
    }

    @Test
    fun `closing the screen shuts the socket`() = vmTest {
        val owner = androidx.lifecycle.ViewModelStore()
        androidx.lifecycle.ViewModelProvider.create(
            owner,
            androidx.lifecycle.viewmodel.viewModelFactory {
                initializer { ButtonDebugViewModel(wifi, { _, _ -> socket }, store, workDispatcher = main.dispatcher) }
            },
        )[ButtonDebugViewModel::class]
        owner.clear()
        assertTrue(socket.shutdownCalled)
    }
}
