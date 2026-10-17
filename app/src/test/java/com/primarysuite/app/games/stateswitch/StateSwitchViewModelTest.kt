package com.primarysuite.app.games.stateswitch

import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.testing.FakeGameConnection
import com.primarysuite.app.testing.FakeWifiConnector
import com.primarysuite.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateSwitchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private val vm by lazy { StateSwitchViewModel(wifi) { _, _ -> socket } }
    private val errors = mutableListOf<String>()

    private val settings = StateSwitchSettings("fast-slow", 2.5f, limitEnabled = true, limit = 4)

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.toList(errors) }
        runCurrent()
    }

    /** The socket opens and the server sends state then settings, as handleConnect does. */
    private suspend fun TestScope.connect(s: StateSwitchSettings = settings, actuations: Int = 0) {
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        receive("""{"type":"state-switch-game-state","payload":{"actuations":$actuations,"current":0}}""")
        receive(StateSwitchCommands.settings(s))
    }

    private suspend fun TestScope.receive(json: String) = receive(GameMessage.decode(json)!!)

    private suspend fun TestScope.receive(message: GameMessage) {
        socket.events.emit(SocketEvent.Received(message))
        runCurrent()
    }

    @Test
    fun `connects when wifi comes up`() = runTest(main.dispatcher) {
        start()
        assertEquals(0, socket.connectCalls)
        wifi.connectionState.value = WifiConnectionState.CONNECTED
        runCurrent()
        assertEquals(1, socket.connectCalls)
    }

    @Test
    fun `follows the server's state and settings without asking`() = runTest(main.dispatcher) {
        start()
        assertNull(vm.ui.value.settings)
        connect(actuations = 2)
        assertTrue(vm.ui.value.connected)
        assertEquals(settings, vm.ui.value.settings)
        assertEquals(StateSwitchState(2, 0), vm.ui.value.state)
        assertTrue("nothing to send on connect", socket.sent.isEmpty())

        // Another admin changes them.
        val other = settings.copy(game = "go-stop", timeout = 0f)
        receive(StateSwitchCommands.settings(other))
        assertEquals(other, vm.ui.value.settings)
    }

    @Test
    fun `a settings change is sent and shown at once`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.updateSettings { it.copy(timeout = 4.2f) }
        assertEquals(listOf(StateSwitchCommands.settings(settings.copy(timeout = 4.2f))), socket.sent)
        assertEquals(4.2f, vm.ui.value.settings?.timeout)
    }

    @Test
    fun `unchanged settings are not sent, nor anything before the server's settings`() = runTest(main.dispatcher) {
        start()
        vm.updateSettings { it.copy(timeout = 1f) }
        assertTrue(socket.sent.isEmpty())
        connect()
        vm.updateSettings { it }
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `switch sends a button press unless cooling down or out of presses`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.switch()
        assertEquals(listOf(StateSwitchCommands.press()), socket.sent)

        receive("""{"type":"button-state","payload":{"disabled":true}}""")
        assertTrue(vm.ui.value.cooldown)
        assertFalse(vm.ui.value.canSwitch)
        vm.switch()
        assertEquals(1, socket.sent.size)

        receive("""{"type":"button-state","payload":{"disabled":false}}""")
        receive("""{"type":"state-switch-game-state","payload":{"actuations":4,"current":0}}""")
        assertTrue(vm.ui.value.limitReached)
        vm.switch()
        assertEquals(1, socket.sent.size)

        // Turning the limit off frees the button.
        receive(StateSwitchCommands.settings(settings.copy(limitEnabled = false)))
        assertFalse(vm.ui.value.limitReached)
        assertTrue(vm.ui.value.canSwitch)
    }

    @Test
    fun `reset actuations sends one message`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.resetActuations()
        assertEquals(listOf(StateSwitchCommands.resetActuations()), socket.sent)
    }

    @Test
    fun `reset all resets, then sends the web's defaults like the web admin`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.resetAll()
        assertEquals(
            listOf(StateSwitchCommands.reset(), StateSwitchCommands.settings(StateSwitchSettings.WEB_DEFAULTS)),
            socket.sent,
        )
        assertEquals(StateSwitchSettings.WEB_DEFAULTS, vm.ui.value.settings)

        // The server's own reset broadcast (0 s timeout) is then replaced by ours.
        receive(StateSwitchCommands.settings(StateSwitchSettings.WEB_DEFAULTS.copy(timeout = 0f)))
        receive(StateSwitchCommands.settings(StateSwitchSettings.WEB_DEFAULTS))
        assertEquals(2.5f, vm.ui.value.settings?.timeout)
    }

    @Test
    fun `failed sends report an error and change nothing`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.acceptSends = false
        vm.updateSettings { it.copy(limit = 10) }
        vm.resetAll()
        assertEquals(settings, vm.ui.value.settings)
        assertEquals(2, errors.size)
    }

    @Test
    fun `losing the socket shows offline but keeps the last settings`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.connected)
        assertFalse(vm.ui.value.canSwitch)
        assertEquals(settings, vm.ui.value.settings)
    }

    @Test
    fun `giving up on the server is reported`() = runTest(main.dispatcher) {
        start()
        socket.events.emit(SocketEvent.GaveUp(10))
        runCurrent()
        assertEquals(1, errors.size)
    }
}
