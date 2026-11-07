package com.primarysuite.app.games.spinwheel

import org.json.JSONObject

/** Builds server `state` payloads like `StateView` in `backend/cmd/spinwheel/engine.go`. */
object Fixtures {
    val values = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
    private val wedges = listOf(100, 600, 200, 700, 300, 800, 400, 900, 500, 1000).let { it + it }

    fun result(id: Int, value: Int, button: Int = 0, rigged: Boolean = false, at: Long = 1_700_000_000_000): String =
        """{"id":$id,"value":$value,"wedge":0,"at":$at,"button":$button,"rigged":$rigged}"""

    fun stateJson(
        phase: String = "armed",
        locked: Boolean = false,
        weights: List<Int> = List(10) { 1 },
        forcedNext: Int? = null,
        settings: JSONObject = WheelSettings.DEFAULTS.toJson(),
        spinValue: Int? = null,
        lastResult: String? = null,
        history: List<String> = emptyList(),
        serverTime: Long = 1_700_000_000_000,
    ): JSONObject = JSONObject(
        """{"phase":"$phase","locked":$locked,"wedges":$wedges,"values":$values,"weights":$weights,""" +
            """"forcedNext":${forcedNext ?: "null"},"settings":$settings,"angle":12.5,""" +
            """"spin":${if (spinValue == null) "null" else """{"id":3,"startAt":1.7e12,"durationMs":9000,"fromAngle":0,"toAngle":3700,"wedge":4,"value":$spinValue}"""},""" +
            """"lastResult":${lastResult ?: "null"},"history":[${history.joinToString(",")}],"serverTime":$serverTime}""",
    )

    fun state(
        phase: String = "armed",
        locked: Boolean = false,
        weights: List<Int> = List(10) { 1 },
        forcedNext: Int? = null,
        spinValue: Int? = null,
        lastResult: String? = null,
        history: List<String> = emptyList(),
    ): WheelState = WheelState.parse(stateJson(phase, locked, weights, forcedNext, spinValue = spinValue, lastResult = lastResult, history = history))
}
