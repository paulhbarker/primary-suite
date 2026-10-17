package com.primarysuite.app.core.config

/**
 * Where the Simple Games server lives. Every game has its own WebSocket channel
 * at `/ws/{slug}`, and the server answers plain HTTP on the same port.
 */
data class ServerConfig(val host: String, val port: String) {

    /** Base HTTP URL, used to check that the server is reachable. */
    val httpUrl: String get() = "http://$host:$port/"

    fun webSocketUrl(gameSlug: String): String {
        require(SLUG.matches(gameSlug)) { "Invalid game slug: '$gameSlug'" }
        return "ws://$host:$port/ws/$gameSlug"
    }

    override fun toString(): String = "$host:$port"

    companion object {
        private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")

        /** True for a TCP port number, 1 to 65535. Surrounding spaces are allowed. */
        fun isValidPort(text: String): Boolean = text.trim().toIntOrNull()?.let { it in 1..65535 } ?: false
    }
}
