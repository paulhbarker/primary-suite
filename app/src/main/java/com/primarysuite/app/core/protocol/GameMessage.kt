package com.primarysuite.app.core.protocol

import org.json.JSONException
import org.json.JSONObject

/**
 * One message on a Simple Games channel. The server sends and receives
 * `{"type": "...", "payload": {...}}` on every `/ws/{slug}` socket.
 */
class GameMessage(val type: String, val payload: JSONObject = JSONObject()) {

    init {
        require(type.isNotBlank()) { "Message type must not be blank" }
    }

    fun encode(): String = JSONObject().put("type", type).put("payload", payload).toString()

    override fun equals(other: Any?): Boolean =
        other is GameMessage && other.type == type && other.payload.toString() == payload.toString()

    override fun hashCode(): Int = 31 * type.hashCode() + payload.toString().hashCode()

    override fun toString(): String = encode()

    companion object {
        /**
         * Parses a frame from the server. Returns null if it isn't a JSON object
         * with a non-blank string `type`. A missing or non-object payload becomes `{}`.
         */
        fun decode(text: String): GameMessage? = try {
            val json = JSONObject(text)
            val type = json.opt("type") as? String
            if (type.isNullOrBlank()) null else GameMessage(type, json.optJSONObject("payload") ?: JSONObject())
        } catch (_: JSONException) {
            null
        }
    }
}
