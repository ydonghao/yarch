package io.github.ydonghao.yarch.client.http

import java.security.SecureRandom
import okhttp3.Interceptor
import okhttp3.Response

/** traceId 生成策略（client-shared 二-1：32 位小写 hex）。 */
public fun interface TraceIdProvider {
    public fun newTraceId(): String
}

/** 默认实现：SecureRandom 16 字节 → 32 位小写 hex。 */
public object SecureRandomTraceIdProvider : TraceIdProvider {
    private val random = SecureRandom()

    override fun newTraceId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

public const val TRACE_ID_HEADER: String = "X-Trace-Id"

/** 出站注入 X-Trace-Id（已存在则不覆盖）；回显校验（响应头优先）见 [Envelope]。 */
public class TraceIdInterceptor(
    private val provider: TraceIdProvider = SecureRandomTraceIdProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val withTrace = if (request.header(TRACE_ID_HEADER).isNullOrBlank()) {
            request.newBuilder().header(TRACE_ID_HEADER, provider.newTraceId()).build()
        } else {
            request
        }
        return chain.proceed(withTrace)
    }
}
