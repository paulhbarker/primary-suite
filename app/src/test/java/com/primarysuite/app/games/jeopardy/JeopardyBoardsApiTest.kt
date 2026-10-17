package com.primarysuite.app.games.jeopardy

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JeopardyBoardsApiTest {

    @Test
    fun `lists boards from the server`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"id":"b1","name":"Science","roundCount":2}]"""))
            val api = HttpJeopardyBoardsApi({ server.url("/").toString() })

            val boards = api.listBoards().getOrThrow()

            assertEquals(listOf(BoardSummary("b1", "Science", 2)), boards)
            assertEquals("/api/jeopardy/boards", server.takeRequest().path)
        }
    }

    @Test
    fun `explains failures`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("not json"))
            val api = HttpJeopardyBoardsApi({ server.url("/").toString() })

            assertEquals("Couldn't load boards (HTTP 503)", api.listBoards().exceptionOrNull()?.message)
            assertEquals("The server sent an unexpected board list", api.listBoards().exceptionOrNull()?.message)
        }
        val down = HttpJeopardyBoardsApi({ "http://127.0.0.1:1" })
        assertEquals("Couldn't reach the game server", down.listBoards().exceptionOrNull()?.message)
        val bad = HttpJeopardyBoardsApi({ "http://bad host:80" })
        assertTrue(bad.listBoards().isFailure)
    }
}
