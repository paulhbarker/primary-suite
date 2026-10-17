package com.primarysuite.app.games.buttondebug

/** How many rows the edge log keeps, as on the web. */
const val MAX_LOG = 200

/** One row of the edge log. [id] is unique, for list keys. */
sealed interface LogRow {
    val id: Long

    data class Edge(override val id: Long, val ev: EdgeEvent) : LogRow
    data class Wifi(override val id: Long, val press: DebugPress) : LogRow
}

/** The snapshot without its history: what the box is. */
data class BoxInfo(val box: Boolean, val boxError: String?, val chip: String?, val buttons: List<DebugButton>)

/**
 * Everything the page shows, apart from the connection and colours. A port of the
 * page's reducer (`ButtonDebug.tsx`): every function returns a new session.
 */
data class DebugSession(
    val info: BoxInfo? = null,
    val status: DebugStatus? = null,
    /** By box index. */
    val stats: Map<Int, Stats> = emptyMap(),
    /** By button id, in ascending id order. */
    val wifi: Map<Int, WifiStats> = emptyMap(),
    /** Newest first. */
    val log: List<LogRow> = emptyList(),
    val firstTsUs: Long? = null,
    private val nextLogId: Long = 0,
) {
    val buttons: List<DebugButton> get() = info?.buttons ?: emptyList()
    val hasBox: Boolean get() = info?.box == true
    val activeGames: List<String> get() = status?.activeGames ?: emptyList()

    fun statsFor(index: Int): Stats = stats[index] ?: emptyStats()

    fun isStuck(index: Int): Boolean = status?.isStuck(index) == true

    /** Ids of buttons a game has disabled. */
    val disabledIds: List<Int>
        get() = status?.let { st -> buttons.filter { !st.isEnabled(it.index) }.map { it.id } } ?: emptyList()

    /** Problems across all buttons. */
    val issueCount: Int get() = buttons.sumOf { problems(statsFor(it.index), isStuck(it.index)).size }

    val totalPresses: Int get() = buttons.sumOf { statsFor(it.index).presses }

    /** Replaces everything with the server's snapshot and replays its history. */
    fun onSnapshot(snap: DebugSnapshot): DebugSession {
        val info = BoxInfo(snap.box, snap.boxError, snap.chip, snap.buttons)
        var next = DebugSession(
            info = info,
            status = snap.status,
            stats = snap.buttons.associate { it.index to emptyStats() },
        )
        // Edges and presses are separate lists, but the per-button stats don't
        // depend on their interleaving.
        for (ev in snap.events) next = next.onEdge(ev, live = false)
        // The server keeps fewer edges than presses' worth of time, so only
        // replay box presses whose edge is still in the history.
        val pressSeqs = snap.events.filter { it.seq != 0L }.map { it.seq }.toSet()
        for (p in snap.presses) {
            if (p.fromBox && p.seq !in pressSeqs) continue
            next = next.onPress(p)
        }
        return next
    }

    fun onStatus(status: DebugStatus): DebugSession = copy(status = status)

    fun onEdge(ev: EdgeEvent, live: Boolean): DebugSession = copy(
        stats = stats + (ev.button to applyEvent(statsFor(ev.button), ev, live)),
        log = pushLog(LogRow.Edge(nextLogId, ev)),
        firstTsUs = firstTsUs ?: ev.tsUs,
        nextLogId = nextLogId + 1,
    )

    fun onPress(p: DebugPress): DebugSession {
        if (p.fromBox) {
            val b = buttons.firstOrNull { it.id == p.id } ?: return this
            return copy(stats = stats + (b.index to applyDelivered(statsFor(b.index), p)))
        }
        return copy(
            wifi = (wifi + (p.id to applyWifi(wifi[p.id], p))).toSortedMap(),
            log = pushLog(LogRow.Wifi(nextLogId, p)),
            nextLogId = nextLogId + 1,
        )
    }

    private fun pushLog(row: LogRow): List<LogRow> {
        val out = ArrayList<LogRow>(minOf(log.size + 1, MAX_LOG))
        out += row
        for (r in log) {
            if (out.size >= MAX_LOG) break
            out += r
        }
        return out
    }
}
