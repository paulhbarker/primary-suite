package com.primarysuite.app.games.blockstacker

import androidx.lifecycle.viewmodel.initializer
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
class BlockStackerViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val wifi = FakeWifiConnector()
    private val socket = FakeGameConnection()
    private var now = 1000L
    private val vm by lazy { BlockStackerViewModel(wifi, { _, _ -> socket }) { now } }
    private val errors = mutableListOf<String>()

    private val server = BlockStackerSettings(20, 2.4, 0.5, true)

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.errors.toList(errors) }
        runCurrent()
    }

    private fun stateMessage(s: BlockStackerSettings, status: String = "waiting", games: Int = 0, high: Int = 0) = GameMessage(
        "block-stacker-game-state",
        JSONObject()
            .put("settings", s.toJson())
            .put("state", JSONObject().put("status", status).put("totalGamesPlayed", games).put("highScore", high).put("lastUpdated", "2026-10-17T08:15:30Z")),
    )

    private suspend fun TestScope.receive(message: GameMessage) {
        socket.events.emit(SocketEvent.Received(message))
        runCurrent()
    }

    /** The socket opens and the server sends the full state, as handleConnect does. */
    private suspend fun TestScope.connect(s: BlockStackerSettings = server) {
        socket.connectionState.value = WebSocketConnectionState.CONNECTED
        socket.events.emit(SocketEvent.Connected)
        receive(stateMessage(s, games = 3, high = 9))
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
    fun `follows the server's full state without asking`() = runTest(main.dispatcher) {
        start()
        assertNull(vm.ui.value.server)
        assertEquals(BlockStackerSettings.DEFAULTS, vm.ui.value.draft)
        connect()
        assertTrue(vm.ui.value.connected)
        assertEquals(server, vm.ui.value.server)
        assertEquals(server, vm.ui.value.draft)
        assertEquals(3, vm.ui.value.state?.totalGamesPlayed)
        assertEquals(9, vm.ui.value.state?.highScore)
        assertFalse(vm.ui.value.dirty)
        assertTrue("nothing to send on connect", socket.sent.isEmpty())

        // Another admin saves.
        val other = server.copy(maxLevels = 5)
        receive(stateMessage(other, status = "active"))
        assertEquals(other, vm.ui.value.draft)
        assertEquals("active", vm.ui.value.state?.status)
    }

    @Test
    fun `editing changes the draft and sends nothing`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.edit { it.copy(initialSpeed = 3.0) }
        vm.edit { it.copy(gameEnabled = false) }
        assertEquals(server.copy(initialSpeed = 3.0, gameEnabled = false), vm.ui.value.draft)
        assertTrue(vm.ui.value.dirty)
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `save sends the draft`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.edit { it.copy(maxLevels = 30) }
        vm.save()
        assertEquals(listOf(BlockStackerCommands.updateSettings(server.copy(maxLevels = 30))), socket.sent)

        // The broadcast confirms it and the draft is clean again.
        receive(stateMessage(server.copy(maxLevels = 30)))
        assertFalse(vm.ui.value.dirty)
        assertEquals(30, vm.ui.value.draft.maxLevels)
    }

    @Test
    fun `unsaved edits survive a broadcast from someone else`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.edit { it.copy(maxLevels = 30) }
        receive(stateMessage(server.copy(speedIncrement = 1.0), games = 4))
        assertEquals(server.copy(maxLevels = 30), vm.ui.value.draft)
        assertEquals(server.copy(speedIncrement = 1.0), vm.ui.value.server)
        assertEquals(4, vm.ui.value.state?.totalGamesPlayed)
        assertTrue(vm.ui.value.dirty)
    }

    @Test
    fun `reset to defaults changes the draft only`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.resetToDefaults()
        assertEquals(BlockStackerSettings.DEFAULTS, vm.ui.value.draft)
        assertEquals(server, vm.ui.value.server)
        assertTrue(vm.ui.value.dirty)
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `test press sends a button press with id 1`() = runTest(main.dispatcher) {
        start()
        connect()
        vm.testPress()
        assertEquals(listOf(BlockStackerCommands.press()), socket.sent)
    }

    @Test
    fun `the log keeps the last ten messages, newest first`() = runTest(main.dispatcher) {
        start()
        connect()
        assertEquals(listOf("block-stacker-game-state"), vm.ui.value.log.map { it.type })
        assertEquals(1000L, vm.ui.value.log.single().timeMillis)

        now = 2000L
        receive(GameMessage("button-press", JSONObject().put("id", 1)))
        val newest = vm.ui.value.log.first()
        assertEquals("button-press", newest.type)
        assertEquals("""{"id":1}""", newest.payload)
        assertEquals(2000L, newest.timeMillis)

        repeat(12) { i ->
            now = 3000L + i
            receive(GameMessage("other-$i"))
        }
        assertEquals(10, vm.ui.value.log.size)
        assertEquals("other-11", vm.ui.value.log.first().type)
        assertEquals("other-2", vm.ui.value.log.last().type)
    }

    @Test
    fun `failed sends report an error`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.acceptSends = false
        vm.save()
        vm.testPress()
        assertEquals(listOf("Not connected to the game server.", "Not connected to the game server."), errors)
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `going offline is shown and the settings stay`() = runTest(main.dispatcher) {
        start()
        connect()
        socket.connectionState.value = WebSocketConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(vm.ui.value.connected)
        assertEquals(server, vm.ui.value.server)
        socket.events.emit(SocketEvent.GaveUp(1))
        runCurrent()
        assertEquals(listOf("Lost the connection to the game server."), errors)
    }

    @Test
    fun `closing the screen shuts the socket`() = runTest(main.dispatcher) {
        val store = androidx.lifecycle.ViewModelStore()
        androidx.lifecycle.ViewModelProvider.create(
            store,
            androidx.lifecycle.viewmodel.viewModelFactory {
                initializer { BlockStackerViewModel(wifi, { _, _ -> socket }) }
            },
        )[BlockStackerViewModel::class]
        store.clear()
        assertTrue(socket.shutdownCalled)
    }
}
