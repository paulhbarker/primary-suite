package com.primarysuite.app.games.luckytap

import android.media.RingtoneManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.net.WebSocketConnectionState
import com.primarysuite.app.core.nfc.NfcState
import com.primarysuite.app.core.ui.UiEvent
import com.primarysuite.app.ui.components.SectionHeader
import com.primarysuite.app.ui.components.StatusCard
import com.primarysuite.app.ui.components.StatusRow
import com.primarysuite.app.ui.components.StatusTone
import com.primarysuite.app.ui.theme.AppColors
import com.primarysuite.app.ui.theme.SocafeFontFamily

object LuckyTapTags {
    const val READ = "luckytap.read"
    const val CHECK_IN = "luckytap.checkIn"
    const val CHECK_OUT = "luckytap.checkOut"
    const val WRITE_INPUT = "luckytap.writeInput"
    const val WRITE = "luckytap.write"
    const val CLEAR = "luckytap.clear"
    const val RESET = "luckytap.reset"
    const val ENABLE_NFC = "luckytap.enableNfc"
    const val STATUS = "luckytap.status"
    const val SCANNING = "luckytap.scanning"
    const val STOP_SCANNING = "luckytap.stopScanning"
    const val SUCCESS = "luckytap.success"
    const val CONFIRM = "luckytap.confirm"
}

/** Everything the Lucky Tap screen draws. */
data class LuckyTapScreenState(
    val uiState: AppUiState,
    val mode: NfcOperationMode,
    val nfcState: NfcState,
    val wifiConnected: Boolean,
    val socketState: WebSocketConnectionState,
    val connectionsActive: Boolean,
    val statusText: String,
    val scanningMessage: String?,
    val successNumber: String?,
    val writeInput: String,
    val writeEnabled: Boolean,
)

private enum class Confirm { RESET, CLEAR }

/** Wires [LuckyTapScreen] to its view model: back handling, toasts, sound, screen-on. */
@Composable
fun LuckyTapRoute(vm: LuckyTapViewModel, onBack: () -> Unit, onEnableNfc: () -> Unit) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val successNumber by vm.numberForSuccessDisplay.collectAsStateWithLifecycle()
    val state = LuckyTapScreenState(
        uiState = uiState,
        mode = vm.currentNfcMode.collectAsStateWithLifecycle().value,
        nfcState = vm.nfcState.collectAsStateWithLifecycle().value,
        wifiConnected = vm.isWifiConnected.collectAsStateWithLifecycle().value,
        socketState = vm.webSocketConnectionState.collectAsStateWithLifecycle().value,
        connectionsActive = vm.areEssentialConnectionsActive.collectAsStateWithLifecycle().value,
        statusText = vm.connectionStatusText.collectAsStateWithLifecycle().value,
        scanningMessage = vm.nfcStatusMessage.collectAsStateWithLifecycle().value,
        successNumber = successNumber,
        writeInput = vm.writeInput.collectAsStateWithLifecycle().value,
        writeEnabled = vm.isWriteButtonEnabled.collectAsStateWithLifecycle().value,
    )

    BackHandler(enabled = uiState != AppUiState.NORMAL) { vm.onBackButtonPressed() }

    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.uiEvents.collect { event ->
            if (event is UiEvent.ShowToast) {
                Toast.makeText(context, event.message, if (event.longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(successNumber) {
        if (successNumber != null) playSuccessSound(context)
    }

    // A scanning station sits on a table for a while: keep the screen on while scanning.
    val view = LocalView.current
    DisposableEffect(uiState) {
        view.keepScreenOn = uiState != AppUiState.NORMAL
        onDispose { view.keepScreenOn = false }
    }

    LuckyTapScreen(
        state = state,
        onBack = onBack,
        onEnableNfc = onEnableNfc,
        onRead = vm::onReadButtonModeSelected,
        onCheckIn = vm::onCheckInButtonModeSelected,
        onCheckOut = vm::onCheckOutButtonModeSelected,
        onWriteInputChange = vm::onWriteInputChange,
        onWrite = vm::onWriteButtonModeSelected,
        onResetGame = vm::onResetGameButtonPressed,
        onClearScans = vm::onClearScansButtonPressed,
        onStopScanning = { vm.onBackButtonPressed() },
    )
}

private fun playSuccessSound(context: android.content.Context) {
    try {
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    } catch (e: Exception) {
        Log.e("LuckyTap", "Error playing notification sound", e)
    }
}

@Composable
fun LuckyTapScreen(
    state: LuckyTapScreenState,
    onBack: () -> Unit,
    onEnableNfc: () -> Unit,
    onRead: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onWriteInputChange: (String) -> Unit,
    onWrite: () -> Unit,
    onResetGame: () -> Unit,
    onClearScans: () -> Unit,
    onStopScanning: () -> Unit,
) {
    val background by animateColorAsState(
        when (state.uiState) {
            AppUiState.NORMAL -> AppColors.Background
            AppUiState.SCANNING -> Color.Black
            AppUiState.SUCCESS_DISPLAY -> AppColors.Success
        },
        animationSpec = tween(300),
        label = "background",
    )
    Box(Modifier.fillMaxSize().background(background)) {
        when (state.uiState) {
            AppUiState.NORMAL -> ControlPanel(
                state, onBack, onEnableNfc, onRead, onCheckIn, onCheckOut,
                onWriteInputChange, onWrite, onResetGame, onClearScans,
            )
            AppUiState.SCANNING -> ScanningOverlay(state, onStopScanning)
            AppUiState.SUCCESS_DISPLAY -> SuccessOverlay(state.successNumber.orEmpty())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(
    state: LuckyTapScreenState,
    onBack: () -> Unit,
    onEnableNfc: () -> Unit,
    onRead: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onWriteInputChange: (String) -> Unit,
    onWrite: () -> Unit,
    onResetGame: () -> Unit,
    onClearScans: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    val focusManager = LocalFocusManager.current
    val canScan = state.connectionsActive
    val canWrite = state.writeEnabled && state.nfcState == NfcState.AVAILABLE

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lucky_tap_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(state)
            when (state.nfcState) {
                NfcState.DISABLED -> FilledTonalButton(
                    onClick = onEnableNfc,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(LuckyTapTags.ENABLE_NFC),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppColors.SurfaceVariant, contentColor = AppColors.Warning),
                ) { Text(stringResource(R.string.button_enable_nfc), fontWeight = FontWeight.Bold) }
                NfcState.UNAVAILABLE -> Text(
                    stringResource(R.string.nfc_no_hardware),
                    color = AppColors.Error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                NfcState.AVAILABLE -> Unit
            }

            SectionHeader(stringResource(R.string.lucky_tap_section_scan))
            Button(
                onClick = { focusManager.clearFocus(); onRead() },
                enabled = canScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(LuckyTapTags.READ),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
            ) { Text(stringResource(R.string.button_start_scanning), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(R.string.button_check_in, canScan, LuckyTapTags.CHECK_IN, Modifier.weight(1f)) {
                    focusManager.clearFocus(); onCheckIn()
                }
                SecondaryButton(R.string.button_check_out, canScan, LuckyTapTags.CHECK_OUT, Modifier.weight(1f)) {
                    focusManager.clearFocus(); onCheckOut()
                }
            }

            SectionHeader(stringResource(R.string.section_write), Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = state.writeInput,
                onValueChange = onWriteInputChange,
                label = { Text(stringResource(R.string.hint_enter_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag(LuckyTapTags.WRITE_INPUT),
            )
            FilledTonalButton(
                onClick = { focusManager.clearFocus(); onWrite() },
                enabled = canWrite,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(LuckyTapTags.WRITE),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppColors.SurfaceVariant, contentColor = AppColors.SecondaryBlue),
            ) { Text(stringResource(R.string.button_write_nfc)) }

            SectionHeader(stringResource(R.string.section_admin), Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DestructiveButton(R.string.button_clear_scans, canScan, LuckyTapTags.CLEAR, Modifier.weight(1f)) { confirm = Confirm.CLEAR }
                DestructiveButton(R.string.button_reset_game, canScan, LuckyTapTags.RESET, Modifier.weight(1f)) { confirm = Confirm.RESET }
            }

            Text(
                text = state.statusText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(LuckyTapTags.STATUS),
                color = AppColors.OnSurfaceMedium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    confirm?.let { which ->
        val (title, body, action) = when (which) {
            Confirm.RESET -> Triple(R.string.confirm_reset_title, R.string.confirm_reset_body, R.string.confirm_reset_action)
            Confirm.CLEAR -> Triple(R.string.confirm_clear_title, R.string.confirm_clear_body, R.string.confirm_clear_action)
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        if (which == Confirm.RESET) onResetGame() else onClearScans()
                    },
                    modifier = Modifier.testTag(LuckyTapTags.CONFIRM),
                ) { Text(stringResource(action), color = AppColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = AppColors.SurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionCard(state: LuckyTapScreenState) {
    StatusCard {
        val (nfcValue, nfcTone) = when (state.nfcState) {
            NfcState.AVAILABLE -> stringResource(R.string.connection_nfc_available) to StatusTone.OK
            NfcState.DISABLED -> stringResource(R.string.connection_nfc_disabled) to StatusTone.BAD
            NfcState.UNAVAILABLE -> stringResource(R.string.connection_nfc_unavailable) to StatusTone.BAD
        }
        StatusRow(stringResource(R.string.connection_label_nfc), nfcValue, nfcTone)
        StatusRow(
            stringResource(R.string.connection_label_wifi),
            stringResource(if (state.wifiConnected) R.string.connection_value_connected else R.string.connection_value_disconnected),
            if (state.wifiConnected) StatusTone.OK else StatusTone.BAD,
        )
        val (wsValue, wsTone) = when (state.socketState) {
            WebSocketConnectionState.CONNECTED -> stringResource(R.string.connection_value_connected) to StatusTone.OK
            WebSocketConnectionState.CONNECTING, WebSocketConnectionState.CLOSING ->
                stringResource(R.string.connection_value_connecting) to StatusTone.BUSY
            WebSocketConnectionState.DISCONNECTED -> stringResource(R.string.connection_value_disconnected) to StatusTone.BAD
        }
        StatusRow(stringResource(R.string.connection_label_server), wsValue, wsTone)
    }
}

@Composable
private fun SecondaryButton(label: Int, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppColors.SurfaceVariant, contentColor = AppColors.OnSurface),
    ) { Text(stringResource(label), fontWeight = FontWeight.Bold) }
}

@Composable
private fun DestructiveButton(label: Int, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error),
    ) { Text(stringResource(label)) }
}

/** Black screen with a red bar sweeping up and down, as on the original scanner. */
@Composable
private fun ScanningOverlay(state: LuckyTapScreenState, onStop: () -> Unit) {
    val modeLabel = when (state.mode) {
        NfcOperationMode.CHECK_IN -> stringResource(R.string.mode_check_in)
        NfcOperationMode.CHECK_OUT -> stringResource(R.string.mode_check_out)
        else -> stringResource(R.string.mode_read)
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag(LuckyTapTags.SCANNING)) {
        val barHeight = 16.dp
        val travel = maxHeight - barHeight
        val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .offset(y = travel * sweep)
                .background(AppColors.Error)
                .semantics { contentDescription = "scanning" },
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(modeLabel.uppercase(), color = AppColors.OnSurfaceMedium, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(stringResource(R.string.scanning_tap_card), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp)
                .testTag(LuckyTapTags.STOP_SCANNING),
        ) { Text(stringResource(R.string.scanning_cancel), color = AppColors.OnSurfaceMedium) }
    }
}

/** The scanned number, big, on green: readable from across the room. */
@Composable
private fun SuccessOverlay(number: String) {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(number) { scale.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
    val description = stringResource(R.string.cd_scanned_number, number)
    Box(Modifier.fillMaxSize().testTag(LuckyTapTags.SUCCESS), contentAlignment = Alignment.Center) {
        Text(
            text = number,
            modifier = Modifier.scale(scale.value).semantics { contentDescription = description },
            color = Color.White,
            fontSize = 190.sp,
            fontFamily = SocafeFontFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}
