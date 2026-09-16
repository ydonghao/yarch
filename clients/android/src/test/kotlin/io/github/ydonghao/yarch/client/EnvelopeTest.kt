package io.github.ydonghao.yarch.client

import io.github.ydonghao.yarch.client.error.ApiError
import io.github.ydonghao.yarch.client.error.NetworkError
import io.github.ydonghao.yarch.client.http.Envelope
import io.github.ydonghao.yarch.client.http.HttpConfig
import io.github.ydonghao.yarch.client.http.TRACE_ID_HEADER
import io.github.ydonghao.yarch.client.http.YarchHttp
import io.github.ydonghao.yarch.client.model.Page
import io.github.ydonghao.yarch.client.model.RestResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class User(val id: String, val name: String)

interface UsersApi {
    @GET("api/v1/users")
    suspend fun users(@Query("page") page: Int): Response<RestResponse<Page<User>>>
}

private fun jsonBody(body: String): ResponseBody = body.toResponseBody("application/json".toMediaType())

class EnvelopeTest {

    private lateinit var server: MockWebServer
    private lateinit var api: UsersApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = YarchHttp.retrofit(server.url("/").toString(), YarchHttp.okHttp()).create(UsersApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `code 0 returns typed data`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"message":"成功","data":{"list":[{"id":"u1","name":"甲"}],"total":1,"page":1,"pageSize":20},"traceId":"t1"}""",
            ),
        )
        val page = Envelope.call { api.users(1) }!!
        assertEquals(1, page.list.size)
        assertEquals("u1", page.list[0].id)
        assertEquals(1L, page.total)
        assertFalse(page.hasNext)
    }

    @Test
    fun `code 0 with null data returns null`() {
        val response = Response.success(RestResponse<User>(code = 0, message = "成功", data = null, traceId = "t"))
        assertNull(Envelope.unwrap(response))
    }

    @Test
    fun `business error carries four fields`() {
        val response = Response.error<RestResponse<User>>(
            400,
            jsonBody("""{"code":1001,"message":"参数校验失败","data":null,"traceId":"body-trace"}"""),
        )
        val error = assertThrows(ApiError::class.java) { Envelope.unwrap(response) }
        assertEquals(1001, error.code)
        assertEquals(400, error.httpStatus)
        assertEquals("参数校验失败", error.message)
        assertEquals("body-trace", error.traceId)
    }

    @Test
    fun `echo mismatch prefers response header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader(TRACE_ID_HEADER, "header-trace")
                .setBody("""{"code":2001,"message":"未认证","data":null,"traceId":"body-trace"}"""),
        )
        try {
            Envelope.call { api.users(1) }
            fail("expected ApiError")
        } catch (e: ApiError) {
            assertEquals("header-trace", e.traceId)
        }
    }

    @Test
    fun `gateway failure envelope on 5xx maps to ApiError`() {
        val response = Response.error<RestResponse<User>>(
            504,
            jsonBody("""{"code":1008,"message":"上游超时","data":null,"traceId":"gw-trace"}"""),
        )
        val error = assertThrows(ApiError::class.java) { Envelope.unwrap(response) }
        assertEquals(1008, error.code)
        assertEquals(504, error.httpStatus)
    }

    @Test
    fun `non-json error body falls back to transport error`() {
        val response = Response.error<RestResponse<User>>(500, jsonBody("<html>oops</html>"))
        assertThrows(NetworkError::class.java) { Envelope.unwrap(response) }
    }

    @Test
    fun `malformed 2xx body is transport error`() = runTest {
        assertEquals(-1, NetworkError.CODE)
        server.enqueue(MockResponse().setBody("not json at all"))
        try {
            Envelope.call { api.users(1) }
            fail("expected NetworkError")
        } catch (_: NetworkError) {
        }
    }

    @Test
    fun `transport failure is NetworkError not ApiError`() = runTest {
        val dead = MockWebServer()
        dead.start()
        val baseUrl = dead.url("/").toString()
        dead.shutdown()
        val deadApi = YarchHttp.retrofit(baseUrl, YarchHttp.okHttp()).create(UsersApi::class.java)
        try {
            Envelope.call { deadApi.users(1) }
            fail("expected NetworkError")
        } catch (_: NetworkError) {
        }
    }

    @Test
    fun `cancellation propagates as native exception`() = runTest {
        val block: suspend () -> Response<RestResponse<User>> = { throw CancellationException("cancelled") }
        try {
            Envelope.call(block)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `nextCursor blank means no next page`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"message":"成功","data":{"list":[],"total":0,"page":2,"pageSize":20,"nextCursor":""},"traceId":"t"}""",
            ),
        )
        val page = Envelope.call { api.users(2) }!!
        assertFalse(page.hasNext)
        server.enqueue(
            MockResponse().setBody(
                """{"code":0,"message":"成功","data":{"list":[],"total":0,"page":1,"pageSize":20,"nextCursor":"cur-1"},"traceId":"t"}""",
            ),
        )
        assertTrue(Envelope.call { api.users(1) }!!.hasNext)
    }

    @Test
    fun `http config is the single timeout point`() {
        val client = YarchHttp.okHttp(
            HttpConfig(connectTimeout = 1.seconds, readTimeout = 2.seconds, writeTimeout = 3.seconds),
        )
        assertEquals(1000, client.connectTimeoutMillis)
        assertEquals(2000, client.readTimeoutMillis)
        assertEquals(3000, client.writeTimeoutMillis)
    }
}
