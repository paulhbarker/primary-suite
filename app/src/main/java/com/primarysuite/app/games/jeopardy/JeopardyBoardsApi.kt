package com.primarysuite.app.games.jeopardy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Lists the server's Jeopardy boards. The same HTTP API the web editor and host use. */
fun interface JeopardyBoardsApi {
    /** The boards, or a failure with a message the host can read. */
    suspend fun listBoards(): Result<List<BoardSummary>>
}

class HttpJeopardyBoardsApi(
    private val baseUrl: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : JeopardyBoardsApi {

    override suspend fun listBoards(): Result<List<BoardSummary>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl().trimEnd('/') + "/api/jeopardy/boards").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Couldn't load boards (HTTP ${response.code})"))
                }
                Result.success(JeopardyParser.boards(JSONArray(body)))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Couldn't reach the game server", e))
        } catch (e: JSONException) {
            Result.failure(IOException("The server sent an unexpected board list", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(IOException("The server address isn't valid", e))
        }
    }
}
