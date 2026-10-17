package com.primarysuite.app.games.reelrush

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
class ReelRushViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private val vm by lazy { ReelRushViewModel(wifi) { _, _ -> socket } }
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

    private suspend fun TestScope.receiveState(json: JSONObject) {
        socket.events.emit(SocketEvent.Received(GameMessage(ReelRushProtocol.STATE, json)))
        runCurrent()
    }

    /** Opens the socket and receives the state the server sends after hello. */
    private suspend fun TestScope.connect(json: JSONObject = Fixtures.stateJson()) {
        open()
        receiveState(json)
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
        assertEquals(listOf(ReelRushCommands.hello()), socket.sent)
        assertTrue(vm.ui.value.connected)
        // A reconnect.
        open()
        assertEquals(listOf(ReelRushCommands.hello(), ReelRushCommands.hello()), socket.sent)
    }

    @Test
    fun `has no game until the server sends one, then follows every broadcast`() = runTest(main.dispatcher) {
        start()
        assertNull(vm.ui.value.state)
        open()
        assertNull(vm.ui.value.state)
        receiveState(Fixtures.stateJson())
        assertEquals(Phase.IDLE, vm.ui.value.state?.phase)

        receiveState(Fixtures.stateJson(phase = "lobby", players = Fixtures.players(true, true, false, false)))
        assertEquals(Phase.LOBBY, vm.ui.value.state?.phase)
        assertEquals(2, vm.ui.value.state?.joinedCount)
    }

    @Test
    fun `ignores events, clock pongs and unknown messages`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.events.emit(SocketEvent.Received(GameMessage("event", JSONObject("""{"kind":"press","slot":1}"""))))
        socket.events.emit(SocketEvent.Received(GameMessage("clock-pong", JSONObject("""{"t0":1,"server":2}"""))))
        socket.events.emit(SocketEvent.Received(GameMessage("surprise")))
        runCurrent()
        assertEquals(Phase.IDLE, vm.ui.value.state?.phase)
        assertTrue(errors.isEmpty())
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `server errors reach the snackbar`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.events.emit(SocketEvent.Received(GameMessage("error", JSONObject("""{"message":"need at least 2 players"}"""))))
        runCurrent()
        assertEquals(listOf("need at least 2 players"), errors)
        socket.events.emit(SocketEvent.Received(GameMessage("error")))
        runCurrent()
        assertEquals(2, errors.size)
        assertTrue(errors[1].isNotBlank())
    }

    @Test
    fun `every action sends its message`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.openLobby()
        vm.removePlayer(2)
        vm.startGame()
        vm.closeRound()
        vm.nextRound()
        vm.replayRound()
        vm.endGame()
        vm.reset()
        vm.saveSettings(Fixtures.settings.copy(reels = 4))
        assertEquals(
            listOf(
                ReelRushCommands.openLobby(),
                ReelRushCommands.removePlayer(2),
                ReelRushCommands.startGame(),
                ReelRushCommands.closeRound(),
                ReelRushCommands.nextRound(),
                ReelRushCommands.replayRound(),
                ReelRushCommands.endGame(),
                ReelRushCommands.reset(),
                ReelRushCommands.settings(Fixtures.settings.copy(reels = 4)),
            ),
            socket.sent,
        )
    }

    @Test
    fun `saving settings does not change the screen until the server answers`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.saveSettings(Fixtures.settings.copy(reels = 4))
        assertEquals(3, vm.ui.value.state?.settings?.reels)
        receiveState(Fixtures.stateJson(settings = Fixtures.settings.copy(reels = 4).toJson()))
        assertEquals(4, vm.ui.value.state?.settings?.reels)
    }

    @Test
    fun `failed sends report an error`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.acceptSends = false
        assertFalse(vm.openLobby())
        vm.saveSettings(Fixtures.settings)
        assertEquals(2, errors.size)
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `losing the socket shows offline but keeps the last game`() = runTest(main.dispatcher) {
        start()
        connect(Fixtures.stateJson(phase = "lobby"))
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.connected)
        assertEquals(Phase.LOBBY, vm.ui.value.state?.phase)
    }

    @Test
    fun `giving up on the server is reported`() = runTest(main.dispatcher) {
        start()
        socket.events.emit(SocketEvent.GaveUp(10))
        runCurrent()
        assertEquals(1, errors.size)
    }

    @Test
    fun `the socket slug matches the server path`() {
        assertEquals("reel-rush", ReelRushViewModel.GAME_SLUG)
    }
}
