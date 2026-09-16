import Foundation
import Testing
@testable import ContractKit

struct User: Codable, Sendable {
    let id: String
    let name: String
}

/// 单一套件 + .serialized：MockURLProtocol 的 handler 是全局单桩，套件间并行会串台。
@Suite(.serialized)
struct ClientKitTests {
    private let baseURL = URL(string: "https://mock.test")!

    private func makeClient(
        token: TokenProvider? = nil,
        onUnauthorized: (@Sendable () async -> Bool)? = nil
    ) -> ApiClient {
        ApiClient(options: ApiClient.Options(baseURL: baseURL, tokenProvider: token, onUnauthorized: onUnauthorized, session: makeMockSession()))
    }

    private func envelope(_ code: Int, _ message: String, data: String = "null", traceId: String = "t1") -> Data {
        Data("{\"code\":\(code),\"message\":\"\(message)\",\"data\":\(data),\"traceId\":\"\(traceId)\"}".utf8)
    }

    private func okEnvelope() -> Data {
        Data(#"{"code":0,"message":"成功","data":{"id":"u1","name":"甲"},"traceId":"t"}"#.utf8)
    }

    // MARK: - 信封语义

    @Test("code=0 返回类型化 data")
    func code0ReturnsTypedData() async throws {
        MockURLProtocol.setHandler { _ in
            (200, [:], self.envelope(0, "成功", data: #"{"id":"u1","name":"甲"}"#))
        }
        let user: User? = try await makeClient().get("api/v1/me")
        #expect(user?.id == "u1")
        #expect(user?.name == "甲")
    }

    @Test("code=0 且 data 为 null 返回 nil")
    func code0NullDataReturnsNil() async throws {
        MockURLProtocol.setHandler { _ in (200, [:], self.envelope(0, "成功")) }
        let user: User? = try await makeClient().get("api/v1/me")
        #expect(user == nil)
    }

    @Test("业务错误四要素齐备")
    func businessErrorCarriesFourFields() async throws {
        MockURLProtocol.setHandler { _ in
            (400, [:], self.envelope(1001, "参数校验失败", traceId: "body-trace"))
        }
        do {
            let user: User? = try await makeClient().get("api/v1/me")
            Issue.record("应当抛出 ApiError，实际返回 \(String(describing: user))")
        } catch let error as ApiError {
            #expect(error.code == 1001)
            #expect(error.httpStatus == 400)
            #expect(error.message == "参数校验失败")
            #expect(error.traceId == "body-trace")
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }

    @Test("回显不一致时以响应头为准")
    func echoMismatchPrefersResponseHeader() async throws {
        MockURLProtocol.setHandler { _ in
            (401, [TRACE_ID_HEADER: "header-trace"], self.envelope(2001, "未认证", traceId: "body-trace"))
        }
        do {
            let _: User? = try await makeClient().get("api/v1/me")
            Issue.record("应当抛出 ApiError")
        } catch let error as ApiError {
            #expect(error.traceId == "header-trace")
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }

    @Test("网关 5xx 信封映射 ApiError")
    func gatewayEnvelopeOn5xx() async throws {
        MockURLProtocol.setHandler { _ in
            (504, [:], self.envelope(1008, "上游超时", traceId: "gw-trace"))
        }
        do {
            let _: User? = try await makeClient().get("api/v1/me")
            Issue.record("应当抛出 ApiError")
        } catch let error as ApiError {
            #expect(error.code == 1008)
            #expect(error.httpStatus == 504)
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }

    @Test("非 JSON body 归传输错误")
    func nonJsonBodyIsNetworkError() async throws {
        MockURLProtocol.setHandler { _ in (200, [:], Data("<html>oops</html>".utf8)) }
        do {
            let _: User? = try await makeClient().get("api/v1/me")
            Issue.record("应当抛出 NetworkError")
        } catch is NetworkError {
            #expect(NetworkError.code == -1)
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }

    @Test("传输层失败归 NetworkError")
    func transportFailureIsNetworkError() async throws {
        MockURLProtocol.setHandler { _ in
            throw URLError(.notConnectedToInternet)
        }
        do {
            let _: User? = try await makeClient().get("api/v1/me")
            Issue.record("应当抛出 NetworkError")
        } catch is NetworkError {
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }

    @Test("取消原生传导不转译")
    func cancellationPropagates() async {
        MockURLProtocol.setHandler { _ in
            Thread.sleep(forTimeInterval: 2)
            return (200, [:], Data(#"{"code":0,"message":"ok","data":null,"traceId":"t"}"#.utf8))
        }
        let client = makeClient()
        let task = Task { () -> User? in
            try await client.get("api/v1/me")
        }
        try? await Task.sleep(for: .milliseconds(150))
        task.cancel()
        do {
            _ = try await task.value
            Issue.record("已取消的任务不应正常返回")
        } catch is CancellationError {
        } catch {
            Issue.record("应当是 CancellationError，实际 \(error)")
        }
    }

    @Test("分页解码与 hasNext 语义")
    func pageDecodingAndHasNext() async throws {
        MockURLProtocol.setHandler { _ in
            (
                200, [:],
                Data(
                    #"{"code":0,"message":"成功","data":{"list":[{"id":"u1","name":"a"}],"total":1,"page":1,"pageSize":20},"traceId":"t"}"#.utf8
                )
            )
        }
        let page: Page<User>? = try await makeClient().get("api/v1/users")
        #expect(page?.list.count == 1)
        #expect(page?.total == 1)
        #expect(page?.hasNext == false)

        MockURLProtocol.setHandler { _ in
            (
                200, [:],
                Data(
                    #"{"code":0,"message":"成功","data":{"list":[],"total":0,"page":2,"pageSize":20,"nextCursor":"cur-1"},"traceId":"t"}"#.utf8
                )
            )
        }
        let next: Page<User>? = try await makeClient().get("api/v1/users")
        #expect(next?.hasNext == true)
    }

    @Test("超时配置是唯一单点")
    func timeoutConfigSinglePoint() {
        let config = HttpConfig(connectTimeout: 1, readTimeout: 2, writeTimeout: 3)
        #expect(config.requestTimeout == 3)
        let defaults = HttpConfig()
        #expect(defaults.requestTimeout == 40)
    }

    // MARK: - trace 与认证

    @Test("出站注入 32 位小写 hex 的 X-Trace-Id")
    func traceHeaderInjected() async throws {
        let captured = Box<String?>(nil)
        MockURLProtocol.setHandler { request in
            captured.value = request.value(forHTTPHeaderField: TRACE_ID_HEADER)
            return (200, [:], self.okEnvelope())
        }
        let _: User? = try await makeClient().get("api/v1/me")
        let traceId = captured.value ?? ""
        #expect(traceId.range(of: "^[0-9a-f]{32}$", options: .regularExpression) != nil)
    }

    @Test("已有 trace 头不覆盖")
    func existingTraceHeaderKept() async throws {
        let captured = Box<String?>(nil)
        MockURLProtocol.setHandler { request in
            captured.value = request.value(forHTTPHeaderField: TRACE_ID_HEADER)
            return (200, [:], self.okEnvelope())
        }
        var request = URLRequest(url: baseURL.appendingPathComponent("api/v1/me"))
        request.httpMethod = "GET"
        request.setValue("keep-me", forHTTPHeaderField: TRACE_ID_HEADER)
        let _: User? = try await makeClient().call(request)
        #expect(captured.value == "keep-me")
    }

    @Test("有 token 才注入 Bearer")
    func bearerInjectedOnlyWhenTokenPresent() async throws {
        let withToken = Box<String?>(nil)
        MockURLProtocol.setHandler { request in
            withToken.value = request.value(forHTTPHeaderField: "Authorization")
            return (200, [:], self.okEnvelope())
        }
        let _: User? = try await makeClient(token: { "tok" }).get("api/v1/me")
        #expect(withToken.value == "Bearer tok")

        let anonymous = Box<String?>(nil)
        MockURLProtocol.setHandler { request in
            anonymous.value = request.value(forHTTPHeaderField: "Authorization")
            return (200, [:], self.okEnvelope())
        }
        let _: User? = try await makeClient().get("api/v1/me")
        #expect(anonymous.value == nil)
    }

    @Test("401 单点刷新后重放一次")
    func refreshReplaysOnce() async throws {
        // tokenProvider 每次请求现取（读凭证存储）；onUnauthorized 刷新存储后重放携带新 token
        let tokenStore = Box<String>("old")
        let authHeaders = Box<[String]>([])
        MockURLProtocol.setHandler { request in
            let box = authHeaders.value
            authHeaders.value = box + [request.value(forHTTPHeaderField: "Authorization") ?? ""]
            if box.isEmpty {
                return (
                    401, [:],
                    Data(#"{"code":2001,"message":"未认证","data":null,"traceId":"t"}"#.utf8)
                )
            }
            return (200, [:], self.okEnvelope())
        }
        let user: User? = try await makeClient(
            token: { tokenStore.value },
            onUnauthorized: {
                tokenStore.value = "fresh"
                return true
            }
        ).get("api/v1/me")
        #expect(user?.id == "u1")
        #expect(authHeaders.value == ["Bearer old", "Bearer fresh"])
    }

    @Test("刷新失败落终态 ApiError")
    func refreshFailureSurfacesApiError() async throws {
        MockURLProtocol.setHandler { _ in
            (401, [:], Data(#"{"code":2001,"message":"未认证","data":null,"traceId":"t"}"#.utf8))
        }
        do {
            let _: User? = try await makeClient(
                token: { "old" },
                onUnauthorized: { false }
            ).get("api/v1/me")
            Issue.record("应当抛出 ApiError")
        } catch let error as ApiError {
            #expect(error.code == 2001)
            #expect(error.httpStatus == 401)
        } catch {
            Issue.record("错误类型不对：\(error)")
        }
    }
}
