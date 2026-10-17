package com.primarysuite.app.games.buttondebug

import org.json.JSONArray
import org.json.JSONObject

/** An edge event with sensible defaults. */
fun edge(
    button: Int = 0,
    id: Int = 1,
    kind: String = EdgeKind.EDGE,
    pressed: Boolean = true,
    tsUs: Long = 0,
    gapUs: Long = -1,
    lagUs: Long = 100,
    kernelGap: Long = 0,
    burstUs: Long? = null,
    verdict: String = Verdict.BOUNCE,
    seq: Long = 0,
) = EdgeEvent(button, id, 5, kind, pressed, tsUs, gapUs, lagUs, kernelGap, burstUs, verdict, seq)

fun press(
    id: Int = 1,
    seq: Long = 0,
    source: String = DebugPress.SOURCE_BOX,
    remote: String? = null,
    hubUs: Long = 400,
    rssi: Int = 0,
    freeHeap: Long = 0,
    atMs: Long = 1_700_000_000_000,
) = DebugPress(id, seq, source, remote, hubUs, rssi, 0, freeHeap, atMs)

fun EdgeEvent.toJson(): JSONObject = JSONObject()
    .put("button", button).put("id", id).put("pin", pin).put("kind", kind).put("pressed", pressed)
    .put("tsUs", tsUs).put("gapUs", gapUs).put("lagUs", lagUs).put("verdict", verdict)
    .also { j ->
        if (kernelGap != 0L) j.put("kernelGap", kernelGap)
        if (burstUs != null) j.put("burstUs", burstUs)
        if (seq != 0L) j.put("seq", seq)
    }

fun DebugPress.toJson(): JSONObject = JSONObject()
    .put("id", id).put("source", source).put("hubUs", hubUs).put("atMs", atMs)
    .also { j ->
        if (seq != 0L) j.put("seq", seq)
        if (remote != null) j.put("remote", remote)
        if (rssi != 0) j.put("rssi", rssi)
        if (freeHeap != 0L) j.put("freeHeap", freeHeap)
    }

fun statusJson(
    down: List<Boolean> = listOf(false, false),
    levels: List<Boolean> = listOf(false, false),
    enabled: List<Boolean> = listOf(true, true),
    leds: List<Boolean> = listOf(true, true),
    ledMode: String = "game",
    quietMs: Double = 20.0,
    stuck: List<Boolean> = listOf(false, false),
    activeGames: List<String>? = null,
    droppedEdges: Long = 0,
    levelErr: String? = null,
): JSONObject = JSONObject()
    .put("down", JSONArray(down)).put("levels", JSONArray(levels)).put("enabled", JSONArray(enabled))
    .put("leds", JSONArray(leds)).put("ledMode", ledMode).put("quietMs", quietMs).put("stuck", JSONArray(stuck))
    .put("activeGames", if (activeGames == null) JSONObject.NULL else JSONArray(activeGames))
    .put("droppedEdges", droppedEdges)
    .also { if (levelErr != null) it.put("levelErr", levelErr) }

fun status(
    down: List<Boolean> = listOf(false, false),
    enabled: List<Boolean> = listOf(true, true),
    stuck: List<Boolean> = listOf(false, false),
    leds: List<Boolean> = listOf(true, true),
    levels: List<Boolean> = listOf(false, false),
    ledMode: String = "game",
    quietMs: Double = 20.0,
    activeGames: List<String> = emptyList(),
    droppedEdges: Long = 0,
    levelErr: String? = null,
) = DebugStatus(down, levels, levelErr, enabled, leds, ledMode, quietMs, stuck, activeGames, droppedEdges)

/** Two buttons on a box: index 0 is id 4 (blue), index 1 is id 3 (white). */
val TWO_BUTTONS = listOf(DebugButton(0, 4, 5, 6), DebugButton(1, 3, 13, 19))

fun snapshot(
    box: Boolean = true,
    boxError: String? = null,
    buttons: List<DebugButton> = TWO_BUTTONS,
    status: DebugStatus? = status(),
    events: List<EdgeEvent> = emptyList(),
    presses: List<DebugPress> = emptyList(),
) = DebugSnapshot(box, boxError, if (box) "gpiochip0" else null, buttons, status, events, presses)

fun DebugSnapshot.toJson(): JSONObject = JSONObject()
    .put("box", box)
    .put("buttons", JSONArray(buttons.map { JSONObject().put("index", it.index).put("id", it.id).put("switchPin", it.switchPin).put("ledPin", it.ledPin) }))
    .put("events", JSONArray(events.map { it.toJson() }))
    .put("presses", JSONArray(presses.map { it.toJson() }))
    .also { j ->
        if (boxError != null) j.put("boxError", boxError)
        if (chip != null) j.put("chip", chip)
    }

/** A tap as the server reports it: a press edge, a bounce, a settle; later a release edge, bounce and settle. */
object Tap {
    fun down(startUs: Long, seq: Long = 1, button: Int = 0, id: Int = 4) = listOf(
        edge(button, id, tsUs = startUs, verdict = Verdict.PRESS, seq = seq),
        edge(button, id, pressed = false, tsUs = startUs + 200, verdict = Verdict.BOUNCE),
        edge(button, id, tsUs = startUs + 700, verdict = Verdict.BOUNCE),
        edge(button, id, kind = EdgeKind.SETTLE, tsUs = startUs + 20_700, lagUs = -1, burstUs = 700, verdict = Verdict.HOLDING),
    )

    fun up(startUs: Long, button: Int = 0, id: Int = 4) = listOf(
        edge(button, id, pressed = false, tsUs = startUs, verdict = Verdict.PENDING),
        edge(button, id, tsUs = startUs + 300, verdict = Verdict.BOUNCE),
        edge(button, id, kind = EdgeKind.SETTLE, pressed = false, tsUs = startUs + 20_300, lagUs = -1, burstUs = 300, verdict = Verdict.RELEASE),
    )
}

/** An in-memory [ButtonColorStore] that counts saves. */
class InMemoryButtonColorStore(var saved: Map<Int, ButtonColor> = emptyMap()) : ButtonColorStore {
    var saves = 0
        private set

    override fun load(): Map<Int, ButtonColor> = saved

    override fun save(colors: Map<Int, ButtonColor>) {
        saved = colors
        saves++
    }
}
