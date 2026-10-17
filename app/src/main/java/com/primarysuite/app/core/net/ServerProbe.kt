package com.primarysuite.app.core.net

import com.primarysuite.app.core.config.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Checks whether the game server answers. */
fun interface ServerProbe {
    suspend fun isReachable(server: ServerConfig): Boolean
}

/**
 * Sends `GET http://host:port/`. Any HTTP response counts as reachable: the
 * game server answers unknown paths with 404, which still proves it's up.
 */
class HttpServerProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build(),
) : ServerProbe {
    override suspend fun isReachable(server: ServerConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(server.httpUrl).head().build()).execute().use { true }
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false // malformed host or port
        }
    }
}
