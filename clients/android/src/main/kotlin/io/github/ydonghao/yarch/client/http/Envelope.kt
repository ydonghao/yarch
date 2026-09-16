package io.github.ydonghao.yarch.client.http

import io.github.ydonghao.yarch.client.error.ApiError
import io.github.ydonghao.yarch.client.error.NetworkError
import io.github.ydonghao.yarch.client.model.RestResponse
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.Response

/**
 * 信封解包唯一实现（client-shared 一-1/一-2）：业务代码只经本层拿强类型结果 / 异常，
 * 禁在任何业务模块手解 code/message/data。HTTP 状态码只是传输层信号，2xx/4xx/5xx 一律解析信封。
 */
public object Envelope {

    /** 信封解码配置：忽略未知字段（信封形状演进不破坏既有客户端）。 */
    public val json: Json = Json { ignoreUnknownKeys = true }

    public suspend fun <T> call(block: suspend () -> Response<RestResponse<T>>): T? {
        val response = try {
            block()
        } catch (e: IOException) {
            throw NetworkError(e)
        } catch (e: SerializationException) {
            throw NetworkError(e)
        }
        return unwrap(response)
    }

    public fun <T> unwrap(response: Response<RestResponse<T>>): T? {
        val headerTrace = response.headers()[TRACE_ID_HEADER]?.trim().orEmpty()
        if (response.isSuccessful) {
            val body = response.body() ?: throw NetworkError()
            if (body.code != 0) {
                throw body.toApiError(response.code(), headerTrace)
            }
            return body.data
        }
        // 非 2xx：信封恒在（网关故障面也产出本信封）；解析失败按传输面兜底（client-shared 一-4）
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val envelope = parseErrorEnvelope(raw) ?: throw NetworkError()
        throw envelope.toApiError(response.code(), headerTrace)
    }

    private fun parseErrorEnvelope(raw: String?): RestResponse<JsonElement>? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val serializer = RestResponse.serializer(JsonElement.serializer())
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    /** 回显校验（client-shared 二-2）：响应头与信封 traceId 不一致时以响应头为准（服务端 bug 信号）。 */
    private fun RestResponse<*>.toApiError(httpStatus: Int, headerTrace: String): ApiError {
        val traceId = headerTrace.ifEmpty { traceId }
        return ApiError(code = code, message = message, traceId = traceId, httpStatus = httpStatus)
    }
}
