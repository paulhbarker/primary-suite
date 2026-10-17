package com.primarysuite.app.games.luckytap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.primarysuite.app.R
import com.primarysuite.app.core.net.GameConnection
import com.primarysuite.app.core.net.GameSocketFactory
import com.primarysuite.app.core.net.SocketEvent
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcHub
import com.primarysuite.app.core.nfc.NfcScanResult
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.nfc.NfcTag
import com.primarysuite.app.core.protocol.GameMessage
import com.primarysuite.app.core.ui.StringProvider
import com.primarysuite.app.core.ui.UiEvent
import com.primarysuite.app.core.wifi.WifiConnectionState
import com.primarysuite.app.core.wifi.WifiConnector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class NfcOperationMode { NONE, WRITE, READ, CHECK_IN, CHECK_OUT }
enum class AppUiState { NORMAL, SCANNING, SUCCESS_DISPLAY }

/**
 * Lucky Tap scanning station: NFC read, check-in, check-out and write, plus
 * the admin reset and clear actions, over the `/ws/lucky-tap` socket.
 *
 * The socket lives as long as this view model, which is scoped to the Lucky
 * Tap screen: it opens when the screen opens and closes when the screen closes.
 */
class LuckyTapViewModel(
    private val strings: StringProvider,
    wifi: WifiConnector,
    private val nfc: NfcHub,
    socketFactory: GameSocketFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    companion object {
        private const val TAG = "LuckyTapViewModel"
        const val GAME_SLUG = "lucky-tap"
        const val SUCCESS_DISPLAY_DURATION_MS = 1000L
        const val MAX_WINNERS = 20
    }

    private fun str(resId: Int, vararg args: Any): String = strings.get(resId, *args)

    // --- One-shot UI events ---
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // --- Connections ---
    val isWifiConnected: StateFlow<Boolean> = wifi.connectionState
        .map { it == WifiConnectionState.CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, wifi.connectionState.value == WifiConnectionState.CONNECTED)

    private val socket: GameConnection = socketFactory(viewModelScope) { isWifiConnected.value }
    val webSocketConnectionState: StateFlow<WebSocketConnectionState> = socket.connectionState

    val nfcState: StateFlow<NfcState> = nfc.state

    /** WiFi and the socket are up. Enough for admin actions, which don't need NFC. */
    val isServerConnected: StateFlow<Boolean> = combine(isWifiConnected, webSocketConnectionState) { wifiConnected, ws ->
        wifiConnected && ws == WebSocketConnectionState.CONNECTED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- Live game state from the server ---
    private val _gameState = MutableStateFlow(LuckyTapGameState())
    val gameState: StateFlow<LuckyTapGameState> = _gameState.asStateFlow()

    val areEssentialConnectionsActive: StateFlow<Boolean> = combine(
        nfc.state,
        isWifiConnected,
        webSocketConnectionState,
    ) { nfcState, wifiConnected, wsState ->
        nfcState == NfcState.AVAILABLE && wifiConnected && wsState == WebSocketConnectionState.CONNECTED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- Scanning state ---
    private val _currentNfcMode = MutableStateFlow(NfcOperationMode.NONE)
    val currentNfcMode: StateFlow<NfcOperationMode> = _currentNfcMode.asStateFlow()

    private val _nfcStatusMessage = MutableStateFlow<String?>(null)
    val nfcStatusMessage: StateFlow<String?> = _nfcStatusMessage.asStateFlow()

    private val _uiState = MutableStateFlow(AppUiState.NORMAL)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _numberForSuccessDisplay = MutableStateFlow<String?>(null)
    val numberForSuccessDisplay: StateFlow<String?> = _numberForSuccessDisplay.asStateFlow()

    // --- Write ---
    private val _writeInput = MutableStateFlow("")
    val writeInput: StateFlow<String> = _writeInput.asStateFlow()

    val isWriteButtonEnabled: StateFlow<Boolean> = _writeInput
        .map { CardNumber.isValid(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The status line under the buttons. */
    val connectionStatusText: StateFlow<String> = combine(
        areEssentialConnectionsActive,
        _nfcStatusMessage,
        _uiState,
        nfc.state,
    ) { isActive, nfcMsg, uiState, nfcState ->
        when {
            nfcState != NfcState.AVAILABLE -> str(R.string.toast_enable_nfc)
            isActive -> if (uiState == AppUiState.NORMAL) nfcMsg ?: str(R.string.status_idle) else ""
            else -> str(R.string.status_waiting_for_connections)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, str(R.string.status_waiting_for_connections))

    init {
        // Open the socket whenever WiFi (re)connects.
        viewModelScope.launch {
            isWifiConnected.collect { wifiConnected ->
                if (wifiConnected && webSocketConnectionState.value != WebSocketConnectionState.CONNECTED) {
                    Log.d(TAG, "WiFi connected. Attempting WebSocket connection.")
                    socket.connect()
                }
            }
        }

        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is SocketEvent.Received -> _gameState.update { it.reduce(event.message) }
                    is SocketEvent.GaveUp ->
                        _uiEvents.emit(UiEvent.ShowToast(str(R.string.toast_ws_gave_up, event.attempts)))
                    SocketEvent.Connected -> Unit // the server sends a full state on connect
                }
            }
        }

        // The server sends a full state on every connect; until then, what we have may be stale.
        viewModelScope.launch {
            webSocketConnectionState.collect { ws ->
                if (ws != WebSocketConnectionState.CONNECTED) _gameState.update { it.copy(synced = false, drawing = false) }
            }
        }

        viewModelScope.launch {
            areEssentialConnectionsActive.collect { active -> if (!active) handleConnectionLoss() }
        }

        viewModelScope.launch {
            nfc.tags.collect { tag -> onTagDiscovered(tag) }
        }
    }

    // --- Modes ---

    fun onWriteInputChange(text: String) {
        _writeInput.value = text.filter { it.isDigit() }.take(3)
    }

    fun onWriteButtonModeSelected() {
        val number = CardNumber.parse(_writeInput.value)
        if (number == null) {
            _nfcStatusMessage.value = str(R.string.status_invalid_number)
            return
        }
        resetScanningIfActive()
        _currentNfcMode.value = NfcOperationMode.WRITE
        _nfcStatusMessage.value = str(R.string.status_ready_write, number.toString())
    }

    fun onReadButtonModeSelected() = startScanning(NfcOperationMode.READ, R.string.status_scanning_read)
    fun onCheckInButtonModeSelected() = startScanning(NfcOperationMode.CHECK_IN, R.string.status_scanning_check_in)
    fun onCheckOutButtonModeSelected() = startScanning(NfcOperationMode.CHECK_OUT, R.string.status_scanning_check_out)

    private fun startScanning(mode: NfcOperationMode, statusRes: Int) {
        _currentNfcMode.value = mode
        _uiState.value = AppUiState.SCANNING
        _nfcStatusMessage.value = str(statusRes)
    }

    private fun resetScanningIfActive() {
        if (_uiState.value != AppUiState.NORMAL) {
            _uiState.value = AppUiState.NORMAL
            _numberForSuccessDisplay.value = null
        }
    }

    // --- Tags ---

    /** A card was tapped. What happens depends on the current mode. */
    fun onTagDiscovered(tag: NfcTag) {
        when {
            _uiState.value == AppUiState.SUCCESS_DISPLAY -> Unit // still showing the last number
            _currentNfcMode.value == NfcOperationMode.WRITE -> writeTag(tag)
            _uiState.value == AppUiState.SCANNING -> viewModelScope.launch {
                val result = withContext(ioDispatcher) { tag.readText() }
                onNfcTagScannedForRead(result)
            }
            else -> onNfcTagScanFailed(str(R.string.nfc_not_active_mode))
        }
    }

    private fun writeTag(tag: NfcTag) {
        val number = CardNumber.parse(_writeInput.value)
        if (number == null) {
            onNfcTagScannedForWrite(_writeInput.value, success = false, errorMessage = str(R.string.nfc_invalid_number_at_tap))
            return
        }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { tag.writeText(number.toString()) }
            onNfcTagScannedForWrite(number.toString(), result.success, result.error)
        }
    }

    private fun onNfcTagScannedForWrite(tagData: String, success: Boolean, errorMessage: String? = null) {
        if (success) {
            _nfcStatusMessage.value = str(R.string.status_write_success, tagData)
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_wrote_tag, tagData)))
        } else {
            val err = errorMessage ?: "Unknown error"
            _nfcStatusMessage.value = str(R.string.status_write_error, err)
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_write_error, err)))
        }
        _currentNfcMode.value = NfcOperationMode.NONE
        _uiState.value = AppUiState.NORMAL
    }

    private fun onNfcTagScannedForRead(result: NfcScanResult) {
        if (_uiState.value != AppUiState.SCANNING) return // cancelled while the tag was being read

        if (!result.success || result.data == null) {
            val errorMsg = result.error ?: str(R.string.status_no_ndef)
            _nfcStatusMessage.value = str(R.string.status_read_error, errorMsg)
            returnToIdle()
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_read_error, errorMsg), longDuration = true))
            return
        }

        val messageType = when (_currentNfcMode.value) {
            NfcOperationMode.CHECK_IN -> "nfc-check-in"
            NfcOperationMode.CHECK_OUT -> "nfc-check-out"
            else -> "card-scanned"
        }
        val payload = JSONObject().put("number", result.data.toIntOrNull())
        if (!socket.send(GameMessage(messageType, payload))) {
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_read_error_short), longDuration = true))
            _nfcStatusMessage.value = str(R.string.status_read_error, result.error ?: str(R.string.status_no_ndef))
            returnToIdle()
            return
        }

        // Only show the number (and play the sound) once the server has it.
        _numberForSuccessDisplay.value = result.data
        _uiState.value = AppUiState.SUCCESS_DISPLAY
        viewModelScope.launch {
            delay(SUCCESS_DISPLAY_DURATION_MS)
            _numberForSuccessDisplay.value = null
            val mode = _currentNfcMode.value
            if (areEssentialConnectionsActive.value && mode != NfcOperationMode.NONE && mode != NfcOperationMode.WRITE) {
                _uiState.value = AppUiState.SCANNING
            } else {
                returnToIdle()
            }
        }
    }

    private fun onNfcTagScanFailed(reason: String) {
        _nfcStatusMessage.value = reason
        if (_uiState.value != AppUiState.SCANNING) {
            returnToIdle()
        } else {
            _uiEvents.tryEmit(UiEvent.ShowToast(reason))
        }
    }

    // --- Admin ---

    fun onResetGameButtonPressed() = sendAdmin("admin-reset-game", R.string.status_reset_sent, R.string.status_reset_no_connection)
    fun onClearScansButtonPressed() = sendAdmin("admin-clear-scans", R.string.status_clear_sent, R.string.status_clear_no_connection)
    fun onCheckInAll() = sendAdmin("admin-check-in-all", R.string.status_check_in_all_sent, R.string.status_admin_no_connection)
    fun onScanAll() = sendAdmin("admin-scan-all", R.string.status_scan_all_sent, R.string.status_admin_no_connection)

    /** Asks the server to resend the whole state. The display redraws too, so not during a draw. */
    fun onRefresh() = sendAdmin("admin-get-full-state", null, R.string.status_admin_no_connection)

    /** Sets how many winners the next draw picks. Values below 1 are ignored, as on the server. */
    fun onSetNumWinners(count: Int) {
        if (count < 1 || count > MAX_WINNERS || count == _gameState.value.numWinners) return
        sendAdmin(
            "admin-set-num-winners", R.string.status_num_winners_sent, R.string.status_admin_no_connection,
            payload = JSONObject().put("number", count), statusArg = count,
        )
    }

    /**
     * Admin actions need WiFi and the server, not NFC. They're refused while a
     * draw is animating: most make the server broadcast a full state, which
     * resets the projected display mid-draw.
     */
    private fun sendAdmin(
        type: String,
        sentRes: Int?,
        offlineRes: Int,
        payload: JSONObject = JSONObject(),
        statusArg: Any? = null,
    ) {
        if (_gameState.value.drawing) {
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_wait_for_draw)))
            return
        }
        if (isServerConnected.value && socket.send(GameMessage(type, payload))) {
            if (sentRes != null) _nfcStatusMessage.value = if (statusArg != null) str(sentRes, statusArg) else str(sentRes)
        } else {
            _nfcStatusMessage.value = str(offlineRes)
            _uiEvents.tryEmit(UiEvent.ShowToast(str(R.string.toast_no_connection)))
        }
    }

    // --- Leaving a scan ---

    /** Returns true if it consumed the back press (a scan was cancelled). */
    fun onBackButtonPressed(): Boolean {
        if (_uiState.value == AppUiState.SCANNING || _uiState.value == AppUiState.SUCCESS_DISPLAY) {
            returnToIdle()
            _numberForSuccessDisplay.value = null
            _nfcStatusMessage.value = str(R.string.status_scan_cancelled)
            return true
        }
        return false
    }

    private fun returnToIdle() {
        _currentNfcMode.value = NfcOperationMode.NONE
        _uiState.value = AppUiState.NORMAL
    }

    private fun handleConnectionLoss() {
        if (_uiState.value == AppUiState.SCANNING || _uiState.value == AppUiState.SUCCESS_DISPLAY) {
            Log.d(TAG, "Connection lost during active state. Resetting to NORMAL.")
            returnToIdle()
            _numberForSuccessDisplay.value = null
            _nfcStatusMessage.value = str(R.string.status_connection_lost)
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket.shutdown()
    }
}
