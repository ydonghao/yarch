import Foundation

/// 取凭证端口（client-shared 一-6）：业务侧认证模块实现，nil 表示匿名请求。
public typealias TokenProvider = @Sendable () -> String?

/// 信封客户端（解包唯一实现，client-shared 一-1）：业务代码只经本层拿强类型结果 / 异常，
/// 禁在任何业务模块手解 code/message/data。HTTP 状态码只是传输层信号，2xx/4xx/5xx 一律解析信封。
public struct ApiClient: Sendable {
    public struct Options: Sendable {
        public var baseURL: URL
        public var config: HttpConfig = HttpConfig()
        public var traceId: TraceIdProvider = makeTraceId
        public var tokenProvider: TokenProvider?
        /// 401 单点（client-shared 一-6）：刷新成功返回 true 则重放原请求一次；
        /// 终态（登出/跳登录）归认证模块单点，业务层收到的是终态 ApiError。
        public var onUnauthorized: (@Sendable () async -> Bool)?
        /// 会话注入位（测试用 MockURLProtocol）；缺省按 config 构建 ephemeral 会话。
        public var session: URLSession?

        public init(
            baseURL: URL,
            config: HttpConfig = HttpConfig(),
            traceId: @escaping TraceIdProvider = makeTraceId,
            tokenProvider: TokenProvider? = nil,
            onUnauthorized: (@Sendable () async -> Bool)? = nil,
            session: URLSession? = nil
        ) {
            self.baseURL = baseURL
            self.config = config
            self.traceId = traceId
            self.tokenProvider = tokenProvider
            self.onUnauthorized = onUnauthorized
            self.session = session
        }
    }

    public let options: Options
    private let session: URLSession

    public init(options: Options) {
        self.options = options
        if let provided = options.session {
            session = provided
        } else {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = options.config.requestTimeout
            configuration.timeoutIntervalForResource = options.config.requestTimeout * 2
            session = URLSession(configuration: configuration)
        }
    }

    public init(baseURL: URL) {
        self.init(options: Options(baseURL: baseURL))
    }

    // MARK: - 便捷入口

    public func get<T: Decodable & Sendable>(_ path: String, query: [String: String] = [:]) async throws -> T? {
        var components = URLComponents(
            url: options.baseURL.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        )!
        if !query.isEmpty {
            components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        return try await call(request)
    }

    public func post<B: Encodable & Sendable, T: Decodable & Sendable>(
        _ path: String,
        body: B
    ) async throws -> T? {
        var request = URLRequest(url: options.baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return try await call(request)
    }

    // MARK: - 解包核心

    public func call<T: Decodable & Sendable>(_ request: URLRequest) async throws -> T? {
        try await call(request, attempt: 0)
    }

    private func call<T: Decodable & Sendable>(_ request: URLRequest, attempt: Int) async throws -> T? {
        var prepared = request
        if (prepared.value(forHTTPHeaderField: TRACE_ID_HEADER) ?? "").isEmpty {
            prepared.setValue(options.traceId(), forHTTPHeaderField: TRACE_ID_HEADER)
        }
        if let token = options.tokenProvider?(),
           (prepared.value(forHTTPHeaderField: "Authorization") ?? "").isEmpty {
            prepared.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let http: HTTPURLResponse
        do {
            let (payload, response) = try await session.data(for: prepared)
            guard let typed = response as? HTTPURLResponse else {
                throw NetworkError(reason: "non-HTTP response")
            }
            data = payload
            http = typed
        } catch let error as URLError {
            if error.code == .cancelled {
                // 取消不是错误（client-shared 一-4）：归一为原生 CancellationError 原样传导
                throw CancellationError()
            }
            throw NetworkError(reason: error.localizedDescription)
        }

        let envelope: RestResponse<T>
        do {
            envelope = try JSONDecoder().decode(RestResponse<T>.self, from: data)
        } catch {
            throw NetworkError(reason: "malformed envelope: \(error.localizedDescription)")
        }

        guard envelope.code == 0 else {
            // 回显校验（client-shared 二-2）：响应头与信封不一致时以响应头为准（服务端 bug 信号）
            let headerTrace = (http.value(forHTTPHeaderField: TRACE_ID_HEADER) ?? "")
                .trimmingCharacters(in: .whitespaces)
            let traceId = headerTrace.isEmpty ? envelope.traceId : headerTrace
            let apiError = ApiError(
                code: envelope.code,
                message: envelope.message,
                traceId: traceId,
                httpStatus: http.statusCode
            )
            if apiError.isUnauthorized, attempt == 0, let refresh = options.onUnauthorized, await refresh() {
                return try await call(request, attempt: attempt + 1)
            }
            throw apiError
        }
        return envelope.data
    }
}
