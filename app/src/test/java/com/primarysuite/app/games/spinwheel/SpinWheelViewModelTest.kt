package com.primarysuite.app.games.spinwheel

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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpinWheelViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private var phoneNow = 1_700_000_000_000L
    private val vm by lazy { SpinWheelViewModel(wifi, now = { phoneNow }) { _, _ -> socket } }
    private val errors = mutableListOf<String>()

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.toList(errors) }
        runCurrent()
    }

    private suspend fun TestScope.open() {
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        runCurrent()
    }

    private suspend fun TestScope.receive(type: String, json: JSONObject) {
        socket.events.emit(SocketEvent.Received(GameMessage(type, json)))
        runCurrent()
    }

    private suspend fun TestScope.connect(json: JSONObject = Fixtures.stateJson()) {
        open()
        receive(SpinWheelProtocol.STATE, json)
        socket.sent.clear()
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
    fun `says hello as admin on every connect`() = runTest(main.dispatcher) {
        start()
        open()
        assertEquals(listOf(SpinWheelCommands.hello()), socket.sent)
        assertTrue(vm.ui.value.connected)
        open()
        assertEquals(listOf(SpinWheelCommands.hello(), SpinWheelCommands.hello()), socket.sent)
    }

    @Test
    fun `has no wheel until the server sends one, then follows every broadcast`() = runTest(main.dispatcher) {
        start()
        open()
        assertNull(vm.ui.value.state)
        receive(SpinWheelProtocol.STATE, Fixtures.stateJson())
        assertEquals(WheelPhase.ARMED, vm.ui.value.state?.phase)
        receive(SpinWheelProtocol.STATE, Fixtures.stateJson(phase = "spinning", spinValue = 300))
        assertEquals(WheelPhase.SPINNING, vm.ui.value.state?.phase)
        assertEquals(300, vm.ui.value.state?.spinValue)
    }

    @Test
    fun `measures how far the server's clock is off`() = runTest(main.dispatcher) {
        start()
        phoneNow = 1_700_000_090_000L
        connect(Fixtures.stateJson(serverTime = 1_700_000_000_000L))
        assertEquals(90_000L, vm.ui.value.clockOffsetMs)
    }

    @Test
    fun `sends each command like the web admin`() = runTest(main.dispatcher) {
        start()
        connect(Fixtures.stateJson(weights = listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 2)))
        vm.spin()
        vm.setLocked(true)
        vm.forceNext(800)
        vm.forceNext(null)
        vm.setWeight(300, 4)
        vm.evenOdds()
        vm.saveSettings(WheelSettings(4000, 6000, 2000, 0.5))
        vm.clearHistory()
        assertEquals(
            listOf(
                SpinWheelCommands.spin(),
                SpinWheelCommands.setLocked(true),
                SpinWheelCommands.forceNext(800),
                SpinWheelCommands.forceNext(null),
                SpinWheelCommands.setWeights(listOf(1, 1, 4, 1, 1, 1, 1, 1, 1, 2)),
                SpinWheelCommands.setWeights(List(10) { 1 }),
                SpinWheelCommands.setSettings(WheelSettings(4000, 6000, 2000, 0.5)),
                SpinWheelCommands.clearHistory(),
            ),
            socket.sent,
        )
    }

    @Test
    fun `won't send weights that leave nothing to land on`() = runTest(main.dispatcher) {
        start()
        connect(Fixtures.stateJson(weights = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 3)))
        vm.setWeight(1000, 0)
        vm.setWeight(1000, 11)
        assertEquals(emptyList<GameMessage>(), socket.sent)
    }

    @Test
    fun `server errors and failed sends reach the snackbar`() = runTest(main.dispatcher) {
        start()
        connect()
        receive(SpinWheelProtocol.ERROR, JSONObject().put("message", "the wheel is already spinning"))
        socket.acceptSends = false
        assertFalse(vm.spin())
        assertEquals(listOf("the wheel is already spinning", "Not connected to the game server."), errors)
    }

    @Test
    fun `keeps the wheel and shows offline when the socket drops`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.connected)
        assertEquals(WheelPhase.ARMED, vm.ui.value.state?.phase)
    }
}
