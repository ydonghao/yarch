package io.github.ydonghao.yarch.client

import io.github.ydonghao.yarch.client.http.RefreshAuthenticator
import io.github.ydonghao.yarch.client.http.TRACE_ID_HEADER
import io.github.ydonghao.yarch.client.http.TokenProvider
import io.github.ydonghao.yarch.client.http.YarchHttp
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TraceAndAuthTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `trace header injected as 32 lowercase hex`() {
        server.enqueue(MockResponse().setBody("{}"))
        YarchHttp.okHttp().newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        val recorded = server.takeRequest()
        assertTrue(Regex("^[0-9a-f]{32}$").matches(recorded.getHeader(TRACE_ID_HEADER)!!))
    }

    @Test
    fun `existing trace header is not overwritten`() {
        server.enqueue(MockResponse().setBody("{}"))
        YarchHttp.okHttp().newCall(
            Request.Builder().url(server.url("/")).header(TRACE_ID_HEADER, "keep-me").build(),
        ).execute().close()
        assertEquals("keep-me", server.takeRequest().getHeader(TRACE_ID_HEADER))
    }

    @Test
    fun `bearer injected only when token present`() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val tokens = TokenProvider { "tok" }
        YarchHttp.okHttp(tokens = tokens).newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
        YarchHttp.okHttp(tokens = TokenProvider { null })
            .newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `401 refreshes token and replays exactly once`() {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val client = YarchHttp.okHttp(
            tokens = TokenProvider { "old" },
            authenticator = RefreshAuthenticator { "fresh" },
        )
        client.newCall(Request.Builder().url(server.url("/api/v1/users")).build()).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `refresh failure surfaces the 401 response`() {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"))
        val client = YarchHttp.okHttp(
            tokens = TokenProvider { "old" },
            authenticator = RefreshAuthenticator { null },
        )
        client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { response ->
            assertEquals(401, response.code)
        }
    }
}
