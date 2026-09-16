import Foundation

/// 业务错误（client-shared 一-3/一-4）：服务端可预期拒绝，code 语义见 error-codes.md。
/// 四要素齐备是硬义务——缺 traceId 的 ApiError 是缺陷（排障凭证丢失）。
public struct ApiError: Error, Sendable, CustomStringConvertible {
    public let code: Int
    public let message: String
    public let traceId: String
    public let httpStatus: Int

    public init(code: Int, message: String, traceId: String, httpStatus: Int) {
        self.code = code
        self.message = message
        self.traceId = traceId
        self.httpStatus = httpStatus
    }

    /// 认证失效（client-shared 一-6）：2001/2002 或 HTTP 401，触发单点刷新重放判定。
    public var isUnauthorized: Bool {
        code == 2001 || code == 2002 || httpStatus == 401
    }

    public var description: String {
        "ApiError(code: \(code), httpStatus: \(httpStatus), message: \(message), traceId: \(traceId))"
    }
}

/// 传输错误（超时 / DNS / 断网 / TLS / body 不可解析）：code 本地保留码 -1（不出网）。
/// 取消不是错误——CancellationError 原生传导，禁捕获转译（client-shared 一-4）。
public struct NetworkError: Error, Sendable, CustomStringConvertible {
    public static let code = -1

    public let reason: String

    public init(reason: String) {
        self.reason = reason
    }

    public var description: String {
        "NetworkError(code: \(Self.code), reason: \(reason))"
    }
}
