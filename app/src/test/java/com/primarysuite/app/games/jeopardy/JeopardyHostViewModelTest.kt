package com.primarysuite.app.games.jeopardy

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class JeopardyHostViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private var boardsResult: Result<List<BoardSummary>> = Result.success(listOf(BoardSummary("b1", "Science", 2)))
    private var boardCalls = 0
    private var buzzes = 0
    private var now = 1760700000000

    private val vm: JeopardyHostViewModel by lazy {
        JeopardyHostViewModel(
            wifi = wifi,
            socketFactory = { _, _ -> socket },
            boardsApi = { boardCalls++; boardsResult },
            onBuzz = { buzzes++ },
            clock = { now },
        )
    }

    private val errors = mutableListOf<String>()

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.toList(errors) }
        runCurrent()
    }

    /** The socket opens and the server welcomes us as host. */
    private suspend fun TestScope.connectAsHost() {
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        runCurrent()
        receive("""{"type":"jeopardy-welcome","payload":{"role":"host"}}""")
    }

    private suspend fun TestScope.receive(json: String) {
        socket.events.emit(SocketEvent.Received(GameMessage.decode(json)!!))
        runCurrent()
    }

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
    fun `says hello as host on every connect`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        connectAsHost()

        assertEquals(List(2) { HostCommands.hello() }, socket.sent)
    }

    @Test
    fun `ignores the public view sent before the welcome`() = runTest(main.dispatcher) {
        start()
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        runCurrent()

        receive(Fixtures.message("board"))
        assertNull(vm.state.value)
        assertFalse(vm.connected.value)

        receive("""{"type":"jeopardy-welcome","payload":{"role":"host"}}""")
        receive(Fixtures.message("board"))
        assertEquals(Phase.BOARD, vm.state.value?.phase)
        assertTrue(vm.connected.value)
    }

    @Test
    fun `a dropped socket is not connected, but keeps the last state on screen`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        receive(Fixtures.message("board"))

        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()

        assertFalse(vm.connected.value)
        assertEquals(Phase.BOARD, vm.state.value?.phase)
    }

    @Test
    fun `server errors become messages`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        receive("""{"type":"jeopardy-error","payload":{"message":"pick a board first"}}""")
        assertEquals(listOf("pick a board first"), errors)
    }

    @Test
    fun `a buzz is felt`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        receive("""{"type":"jeopardy-event","payload":{"kind":"buzz","playerId":"p2"}}""")
        receive("""{"type":"jeopardy-event","payload":{"kind":"correct","playerId":"p2"}}""")
        assertEquals(1, buzzes)
    }

    @Test
    fun `commands go to the socket, or explain why not`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        vm.send(HostCommands.arm())
        assertEquals(HostCommands.arm(), socket.sent.last())

        socket.acceptSends = false
        vm.send(HostCommands.arm())
        runCurrent()
        assertEquals(listOf("Not connected to the game server."), errors)
    }

    @Test
    fun `loads boards on connect and on refresh`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        assertEquals(1, boardCalls)
        assertEquals(listOf(BoardSummary("b1", "Science", 2)), vm.boards.value.boards)

        boardsResult = Result.failure(IOException("Couldn't reach the game server"))
        vm.refreshBoards()
        runCurrent()

        assertEquals(2, boardCalls)
        assertEquals("Couldn't reach the game server", vm.boards.value.error)
        assertEquals(1, vm.boards.value.boards?.size) // keeps the last good list
    }

    @Test
    fun `countdown uses the server clock`() = runTest(main.dispatcher) {
        start()
        connectAsHost()
        now = 1759999998000 // phone is behind; serverTime in the fixture is 1760700000000
        receive(Fixtures.message("answering"))

        val c = vm.countdown(nowMs = now)
        assertEquals(5_000, c.remainingMs)
        assertEquals("answer", c.kind)
    }

    @Test
    fun `closing the screen shuts the socket`() = runTest(main.dispatcher) {
        val store = androidx.lifecycle.ViewModelStore()
        androidx.lifecycle.ViewModelProvider.create(
            store,
            androidx.lifecycle.viewmodel.viewModelFactory {
                initializer { JeopardyHostViewModel(wifi, { _, _ -> socket }, { boardsResult }) }
            },
        )[JeopardyHostViewModel::class]
        store.clear()
        assertTrue(socket.shutdownCalled)
    }
}
