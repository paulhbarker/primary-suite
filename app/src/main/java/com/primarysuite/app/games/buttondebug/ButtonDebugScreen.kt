package com.primarysuite.app.games.buttondebug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primarysuite.app.R
import com.primarysuite.app.core.ui.OfflineBadge
import com.primarysuite.app.ui.theme.AppColors

object ButtonDebugTags {
    const val LIST = "buttondebug.list"
    const val CONNECTION = "buttondebug.connection"
    const val RTT = "buttondebug.rtt"
    const val BANNER_OFFLINE = "buttondebug.banner.offline"
    const val BANNER_NO_BOX = "buttondebug.banner.noBox"
    const val BANNER_LEVEL_ERR = "buttondebug.banner.levelErr"
    const val BANNER_DISABLED = "buttondebug.banner.disabled"
    const val BANNER_ENABLE_ALL = "buttondebug.banner.enableAll"
    const val BANNER_ACTIVE_GAMES = "buttondebug.banner.activeGames"
    const val BANNER_DROPPED = "buttondebug.banner.dropped"
    const val SUMMARY = "buttondebug.summary"
    const val CHASE = "buttondebug.chase"
    const val DEBOUNCE_NOW = "buttondebug.debounce.now"
    const val DEBOUNCE_FIELD = "buttondebug.debounce.field"
    const val DEBOUNCE_SET = "buttondebug.debounce.set"
    const val RESET_STATS = "buttondebug.resetStats"
    const val ENABLE_ALL = "buttondebug.enableAll"
    const val WIFI_CARD = "buttondebug.wifi"
    const val LOG_EMPTY = "buttondebug.log.empty"
    const val HOWTO = "buttondebug.howto"
    const val HOWTO_CHASE = "buttondebug.howto.chase"

    fun led(mode: LedMode) = "buttondebug.led.${mode.mode}"
    fun debouncePreset(ms: Int) = "buttondebug.debounce.$ms"
    fun wifi(id: Int) = "buttondebug.wifi.$id"
    fun wifiStat(id: Int, key: String) = "buttondebug.wifi.$id.$key"
    fun logRow(id: Long) = "buttondebug.log.$id"
    fun logPart(id: Long, part: String) = "buttondebug.log.$id.$part"
}

/** Wires the screen to its view model. */
@Composable
fun ButtonDebugRoute(vm: ButtonDebugViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.errors.collect { snackbar.showSnackbar(it) } }

    // The page is for tapping buttons and watching the tiles: don't let the phone sleep.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ButtonDebugScreen(
        ui = ui,
        snackbar = snackbar,
        onLedMode = vm::setLedMode,
        onChase = vm::chase,
        onEnableAll = vm::enableAll,
        onDebounce = vm::setDebounce,
        onReset = vm::resetStats,
        onColor = vm::setColor,
        onBack = onBack,
    )
}

/** The web page (ButtonDebug.tsx) on a phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonDebugScreen(
    ui: ButtonDebugUiState,
    snackbar: SnackbarHostState,
    onLedMode: (LedMode) -> Unit,
    onChase: () -> Unit,
    onEnableAll: () -> Unit,
    onDebounce: (Double) -> Unit,
    onReset: () -> Unit,
    onColor: (Int, ButtonColor) -> Unit,
    onBack: () -> Unit,
) {
    val session = ui.session
    Scaffold(
        containerColor = AppColors.Background,
        contentColor = AppColors.OnSurface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.buttondebug_title))
                        if (!ui.connected && session.info != null) OfflineBadge(stringResource(R.string.game_offline))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag(ButtonDebugTags.LIST),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("header") { Header(ui) }
                item("banners") { Banners(ui, onEnableAll) }

                if (session.hasBox) {
                    item("summary") { Summary(session.issueCount, session.totalPresses) }
                    items(session.buttons.sortedByBoxOrder(ui.colors), key = { "tile-${it.index}" }) { b ->
                        ButtonTile(
                            button = b,
                            stats = session.statsFor(b.index),
                            status = session.status,
                            color = ui.colors.colorFor(b.id),
                            onColor = onColor,
                        )
                    }
                    item("controls") {
                        Controls(session.status, enabled = ui.connected, onLedMode, onChase, onDebounce, onReset, onEnableAll)
                    }
                }

                if (session.wifi.isNotEmpty()) item("wifi") { WifiButtons(session.wifi.values.toList()) }

                item("logHeader") { LogHeader() }
                if (session.log.isEmpty()) {
                    item("logEmpty") {
                        Text(
                            stringResource(R.string.buttondebug_log_empty),
                            color = AppColors.OnSurfaceDim,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(ButtonDebugTags.LOG_EMPTY),
                        )
                    }
                } else {
                    items(session.log, key = { "log-${it.id}" }) { row ->
                        LogRowView(row, session.firstTsUs, ui.colors)
                    }
                }

                item("howto") { HowTo(session.buttons, ui.colors) }
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun Header(ui: ButtonDebugUiState) {
    val info = ui.session.info
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ui.showRtt) {
                Text(
                    stringResource(R.string.buttondebug_rtt, ui.rttMs!!.toInt().coerceAtLeast(0)),
                    color = AppColors.OnSurfaceMedium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .border(1.dp, AppColors.Outline, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag(ButtonDebugTags.RTT),
                )
            }
            val tone = if (ui.connected) AppColors.Success else AppColors.Error
            Text(
                stringResource(if (ui.connected) R.string.buttondebug_connected else R.string.buttondebug_disconnected),
                color = tone,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tone.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag(ButtonDebugTags.CONNECTION),
            )
        }
        Text(
            if (info?.box == true) {
                stringResource(R.string.buttondebug_description_box, info.chip.orEmpty())
            } else {
                stringResource(R.string.buttondebug_description)
            },
            color = AppColors.OnSurfaceMedium,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Banners(ui: ButtonDebugUiState, onEnableAll: () -> Unit) {
    val session = ui.session
    val info = session.info
    val status = session.status
    val disabled = session.disabledIds
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!ui.connected) Banner(true, ButtonDebugTags.BANNER_OFFLINE) { Text(stringResource(R.string.buttondebug_banner_offline)) }
        if (ui.connected && info != null && !info.box) {
            Banner(true, ButtonDebugTags.BANNER_NO_BOX) { Text(stringResource(R.string.buttondebug_banner_no_box, info.boxError.orEmpty())) }
        }
        status?.levelErr?.let { err ->
            Banner(true, ButtonDebugTags.BANNER_LEVEL_ERR) { Text(stringResource(R.string.buttondebug_banner_level_err, err)) }
        }
        if (disabled.isNotEmpty()) {
            Banner(
                danger = false,
                tag = ButtonDebugTags.BANNER_DISABLED,
                action = {
                    OutlinedButton(
                        onClick = onEnableAll,
                        enabled = ui.connected,
                        contentPadding = SmallPadding,
                        modifier = Modifier.heightIn(min = 36.dp).testTag(ButtonDebugTags.BANNER_ENABLE_ALL),
                    ) { Text(stringResource(R.string.buttondebug_enable_all_short), fontSize = 13.sp) }
                },
            ) { Text(pluralStringResource(R.plurals.buttondebug_banner_disabled, disabled.size, disabled.joinToString(", "))) }
        }
        if (session.activeGames.isNotEmpty()) {
            Banner(false, ButtonDebugTags.BANNER_ACTIVE_GAMES) {
                Text(stringResource(R.string.buttondebug_banner_active_games, session.activeGames.joinToString(", ")))
            }
        }
        if ((status?.droppedEdges ?: 0) > 0) {
            Banner(false, ButtonDebugTags.BANNER_DROPPED) { Text(stringResource(R.string.buttondebug_banner_dropped, status!!.droppedEdges)) }
        }
    }
}

private val SmallPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

@Composable
private fun Banner(danger: Boolean, tag: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val tone = if (danger) AppColors.Error else AppColors.Warning
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides tone,
                androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 14.sp),
                content = content,
            )
        }
        action?.invoke()
    }
}

@Composable
private fun Summary(issues: Int, presses: Int) {
    val tone = if (issues > 0) AppColors.Error else AppColors.Success
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(ButtonDebugTags.SUMMARY),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (issues > 0) Icons.Outlined.Warning else Icons.Outlined.CheckCircle, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Text(
            when {
                issues > 0 -> pluralStringResource(R.plurals.buttondebug_summary_problems, issues, issues)
                presses > 0 -> pluralStringResource(R.plurals.buttondebug_summary_ok, presses, presses)
                else -> stringResource(R.string.buttondebug_summary_empty)
            },
            color = tone,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------

private fun ledLabel(mode: LedMode) = when (mode) {
    LedMode.GAME -> R.string.buttondebug_led_game
    LedMode.FOLLOW -> R.string.buttondebug_led_follow
    LedMode.ON -> R.string.buttondebug_led_on
    LedMode.OFF -> R.string.buttondebug_led_off
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    status: DebugStatus?,
    enabled: Boolean,
    onLedMode: (LedMode) -> Unit,
    onChase: () -> Unit,
    onDebounce: (Double) -> Unit,
    onReset: () -> Unit,
    onEnableAll: () -> Unit,
) {
    var debounce by rememberSaveable { mutableStateOf("") }
    val applyDebounce = { ms: Double ->
        onDebounce(ms)
        debounce = ""
    }
    val typed = parseDebounce(debounce)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            SectionTitle(stringResource(R.string.buttondebug_leds))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LedMode.entries.forEach { mode ->
                    ChoiceButton(
                        text = stringResource(ledLabel(mode)),
                        selected = status?.ledMode == mode.mode,
                        enabled = enabled,
                        tag = ButtonDebugTags.led(mode),
                        onClick = { onLedMode(mode) },
                    )
                }
                OutlinedButton(
                    onClick = onChase,
                    enabled = enabled,
                    contentPadding = SmallPadding,
                    modifier = Modifier.heightIn(min = 36.dp).testTag(ButtonDebugTags.CHASE),
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.buttondebug_chase), modifier = Modifier.padding(start = 4.dp), fontSize = 13.sp)
                }
            }
            Note(stringResource(R.string.buttondebug_leds_note))
        }

        Column {
            Row {
                Text(stringResource(R.string.buttondebug_debounce), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    " · " + stringResource(R.string.buttondebug_debounce_now, status?.let { fmtNumber(it.quietMs) + "ms" } ?: DASH),
                    color = AppColors.OnSurfaceMedium,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag(ButtonDebugTags.DEBOUNCE_NOW),
                )
            }
            Spacer6()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                ButtonDebugProtocol.DEBOUNCE_PRESETS.forEach { ms ->
                    ChoiceButton(
                        text = ms.toString(),
                        selected = status?.quietMs == ms.toDouble(),
                        enabled = enabled,
                        tag = ButtonDebugTags.debouncePreset(ms),
                        onClick = { applyDebounce(ms.toDouble()) },
                    )
                }
                OutlinedTextField(
                    value = debounce,
                    onValueChange = { debounce = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.buttondebug_debounce_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { typed?.let(applyDebounce) }),
                    enabled = enabled,
                    modifier = Modifier.widthIn(max = 96.dp).testTag(ButtonDebugTags.DEBOUNCE_FIELD),
                )
                OutlinedButton(
                    onClick = { typed?.let(applyDebounce) },
                    enabled = enabled && typed != null,
                    contentPadding = SmallPadding,
                    modifier = Modifier.heightIn(min = 36.dp).testTag(ButtonDebugTags.DEBOUNCE_SET),
                ) { Text(stringResource(R.string.buttondebug_debounce_set), fontSize = 13.sp) }
            }
            Note(stringResource(R.string.buttondebug_debounce_note))
        }

        Column {
            SectionTitle(stringResource(R.string.buttondebug_session))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = enabled,
                    contentPadding = SmallPadding,
                    modifier = Modifier.heightIn(min = 36.dp).testTag(ButtonDebugTags.RESET_STATS),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.buttondebug_reset_stats), modifier = Modifier.padding(start = 4.dp), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onEnableAll,
                    enabled = enabled,
                    contentPadding = SmallPadding,
                    modifier = Modifier.heightIn(min = 36.dp).testTag(ButtonDebugTags.ENABLE_ALL),
                ) { Text(stringResource(R.string.buttondebug_enable_all), fontSize = 13.sp) }
            }
            Note(stringResource(R.string.buttondebug_session_note))
        }
    }
}

@Composable
private fun Spacer6() = Box(Modifier.size(6.dp))

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = AppColors.OnSurfaceMedium, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

/** A small button that is filled green when it is the current setting. */
@Composable
private fun ChoiceButton(text: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val modifier = Modifier.heightIn(min = 36.dp).testTag(tag)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = SmallPadding,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryGreen, contentColor = AppColors.OnPrimary),
            modifier = modifier,
        ) { Text(text, fontSize = 13.sp) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = SmallPadding, modifier = modifier) { Text(text, fontSize = 13.sp) }
    }
}

// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WifiButtons(buttons: List<WifiStats>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp)).padding(16.dp).testTag(ButtonDebugTags.WIFI_CARD),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.buttondebug_wifi_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            buttons.forEach { w -> WifiTile(w, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun WifiTile(w: WifiStats, modifier: Modifier) {
    val flash = rememberPulse(w.pressKey, active = true, millis = 400)
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                val p = flash.value
                if (p < 1f) drawRect(AppColors.SecondaryBlue.copy(alpha = 0.2f * (1f - p)))
            }
            .border(1.dp, AppColors.Outline, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag(ButtonDebugTags.wifi(w.id)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.buttondebug_wifi_button, w.id), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        StatRow(stringResource(R.string.buttondebug_wifi_presses), w.presses.toString(), ButtonDebugTags.wifiStat(w.id, "presses"))
        StatRow(stringResource(R.string.buttondebug_wifi_lost), w.lostSeq.toString(), ButtonDebugTags.wifiStat(w.id, "lost"), bad = w.lostSeq > 0)
        StatRow(
            stringResource(R.string.buttondebug_wifi_rssi),
            w.rssi?.let { stringResource(R.string.buttondebug_wifi_rssi_value, it) } ?: DASH,
            ButtonDebugTags.wifiStat(w.id, "rssi"),
            bad = w.rssi != null && w.rssi < -75,
        )
        w.remote?.let { StatRow(stringResource(R.string.buttondebug_wifi_from), it, ButtonDebugTags.wifiStat(w.id, "from")) }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun LogHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(AppColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.buttondebug_log_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(stringResource(R.string.buttondebug_log_hint, MAX_LOG), color = AppColors.OnSurfaceMedium, fontSize = 12.sp)
    }
}

private fun verdictColors(verdict: String): Pair<Color, Color> = when (verdict) {
    Verdict.PRESS, Verdict.LATE_PRESS -> AppColors.Success.copy(alpha = 0.15f) to AppColors.Success
    Verdict.LOST_PRESS, Verdict.LOST_RELEASE, Verdict.DISABLED -> AppColors.Error.copy(alpha = 0.15f) to AppColors.Error
    Verdict.RELEASE -> AppColors.SecondaryBlue.copy(alpha = 0.15f) to AppColors.SecondaryBlue
    Verdict.BLIP -> AppColors.Warning.copy(alpha = 0.15f) to AppColors.Warning
    Verdict.BOUNCE -> AppColors.SurfaceVariant to AppColors.OnSurfaceDim
    else -> AppColors.SurfaceVariant to AppColors.OnSurfaceMedium // holding, pending, unknown
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogRowView(row: LogRow, firstTsUs: Long?, colors: Map<Int, ButtonColor>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(ButtonDebugTags.logRow(row.id)),
    ) {
        when (row) {
            is LogRow.Wifi -> {
                val p = row.press
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    LogText(clockTime(p.atMs), AppColors.OnSurfaceMedium, ButtonDebugTags.logPart(row.id, "time"))
                    LogText("#${p.id}", AppColors.OnSurface, ButtonDebugTags.logPart(row.id, "button"))
                    LogText(stringResource(R.string.buttondebug_log_wifi), AppColors.OnSurface, ButtonDebugTags.logPart(row.id, "event"))
                    val (bg, fg) = verdictColors(Verdict.PRESS)
                    Chip(stringResource(R.string.buttondebug_log_press) + if (p.seq != 0L) " #${p.seq}" else "", bg, fg, ButtonDebugTags.logPart(row.id, "verdict"))
                    if (p.rssi != 0) LogText(stringResource(R.string.buttondebug_wifi_rssi_value, p.rssi), AppColors.OnSurfaceMedium, ButtonDebugTags.logPart(row.id, "rssi"))
                }
            }
            is LogRow.Edge -> {
                val ev = row.ev
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    LogText(logTime(ev, firstTsUs), AppColors.OnSurfaceMedium, ButtonDebugTags.logPart(row.id, "time"))
                    ColorDot(colors[ev.id])
                    LogText("#${ev.id}", AppColors.OnSurface, ButtonDebugTags.logPart(row.id, "button"))
                    LogText(logEvent(ev), if (ev.kind == EdgeKind.EDGE) AppColors.OnSurfaceMedium else AppColors.OnSurface, ButtonDebugTags.logPart(row.id, "event"))
                    val (bg, fg) = verdictColors(ev.verdict)
                    Chip(logVerdict(ev), bg, fg, ButtonDebugTags.logPart(row.id, "verdict"))
                    if (ev.kernelGap != 0L) {
                        LogText(stringResource(R.string.buttondebug_log_lost, ev.kernelGap), AppColors.Error, ButtonDebugTags.logPart(row.id, "lost"))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LogText(stringResource(R.string.buttondebug_log_gap, logGap(ev)), AppColors.OnSurfaceDim, ButtonDebugTags.logPart(row.id, "gap"))
                    LogText(
                        stringResource(if (ev.kind == EdgeKind.EDGE) R.string.buttondebug_log_lag else R.string.buttondebug_log_burst, logLagOrBurst(ev)),
                        AppColors.OnSurfaceDim,
                        ButtonDebugTags.logPart(row.id, "lag"),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogText(text: String, color: Color, tag: String) {
    Text(text, color = color, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.testTag(tag))
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color, tag: String) {
    Text(
        text,
        color = fg,
        fontFamily = Mono,
        fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp).testTag(tag),
    )
}

@Composable
private fun ColorDot(color: ButtonColor?) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color?.bg?.asColor() ?: AppColors.OnSurfaceDim)
            .border(1.dp, color?.edge?.asColor() ?: Color.Transparent, CircleShape),
    )
}

// ---------------------------------------------------------------------------

@Composable
private fun HowTo(buttons: List<DebugButton>, colors: Map<Int, ButtonColor>) {
    val order = buttons.sortedBy { it.id }.joinToString(", ") { colors.colorFor(it.id).label.lowercase() }
        .ifEmpty { stringResource(R.string.buttondebug_howto_order_unknown) }
    val steps = listOf(
        stringResource(R.string.buttondebug_howto_1),
        stringResource(R.string.buttondebug_howto_2, order),
        stringResource(R.string.buttondebug_howto_3),
        stringResource(R.string.buttondebug_howto_4),
        stringResource(R.string.buttondebug_howto_5),
        stringResource(R.string.buttondebug_howto_6),
        stringResource(R.string.buttondebug_howto_7),
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.Surface).border(1.dp, AppColors.Outline, RoundedCornerShape(12.dp)).padding(16.dp).testTag(ButtonDebugTags.HOWTO),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.buttondebug_howto_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        steps.forEachIndexed { n, text ->
            Text(
                "${n + 1}.  $text",
                color = AppColors.OnSurfaceMedium,
                fontSize = 13.sp,
                modifier = if (n == 1) Modifier.testTag(ButtonDebugTags.HOWTO_CHASE) else Modifier,
            )
        }
    }
}
