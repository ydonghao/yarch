package io.github.ydonghao.yarch.client.error

/**
 * 业务错误（client-shared 一-3/一-4）：服务端可预期拒绝，code 语义见
 * contract/api/error-codes.md。四要素齐备是硬义务——缺 traceId 的 ApiError 是缺陷（排障凭证丢失）。
 */
public class ApiError(
    public val code: Int,
    message: String,
    public val traceId: String,
    public val httpStatus: Int,
) : RuntimeException(message) {
    override fun toString(): String =
        "ApiError(code=$code, httpStatus=$httpStatus, message=$message, traceId=$traceId)"
}

/**
 * 传输错误（超时 / DNS / 断网 / TLS 失败 / body 不可解析）：
 * code 本地保留码 -1（不出网），提示口径统一为网络类文案。
 * 取消不是错误——CancellationException 原生传导，禁在本层捕获转译。
 */
public class NetworkError(
    cause: Throwable? = null,
) : RuntimeException("network transport failure (code=-1)", cause) {
    public companion object {
        public const val CODE: Int = -1
    }
}
