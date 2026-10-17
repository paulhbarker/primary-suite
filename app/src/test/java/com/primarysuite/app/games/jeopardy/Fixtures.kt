package com.primarysuite.app.games.jeopardy

import com.primarysuite.app.core.protocol.GameMessage

/** Server messages captured in the shape simple-games marshals them (test/resources/jeopardy). */
object Fixtures {
    fun message(name: String): GameMessage {
        val text = checkNotNull(javaClass.getResourceAsStream("/jeopardy/$name.json")) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }
        return checkNotNull(GameMessage.decode(text))
    }

    fun state(name: String): HostState = JeopardyParser.state(message(name).payload)
}
