import Foundation

typealias MockHandler = @Sendable (URLRequest) throws -> (status: Int, headers: [String: String], body: Data)

/// URLProtocol 级请求桩：handler 全局唯一（锁保护），测试串行执行（@Suite(.serialized)）防串扰。
final class MockURLProtocol: URLProtocol {
    private static let lock = NSLock()

    // 访问由 lock 保护（外部同步机制），Swift 6 严格并发下显式声明 nonisolated(unsafe)
    private nonisolated(unsafe) static var handler: MockHandler?

    static func setHandler(_ next: MockHandler?) {
        lock.lock()
        defer { lock.unlock() }
        handler = next
    }

    private static func currentHandler() -> MockHandler? {
        lock.lock()
        defer { lock.unlock() }
        return handler
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override class func requestIsCacheEquivalent(_ a: URLRequest, to b: URLRequest) -> Bool { false }

    override func startLoading() {
        guard let handler = MockURLProtocol.currentHandler() else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        do {
            let result = try handler(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: result.status,
                httpVersion: "HTTP/1.1",
                headerFields: result.headers
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: result.body)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

func makeMockSession() -> URLSession {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MockURLProtocol.self]
    return URLSession(configuration: configuration)
}

/// 并发安全的值盒（handler 闭包里捕获断言值用）；带初值、value 非 Optional——
/// 避免双层 Optional（Box<String?> 的 .some(nil) == nil 判假）这类语义陷阱。
final class Box<T>: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: T

    init(_ initial: T) {
        stored = initial
    }

    var value: T {
        get {
            lock.lock()
            defer { lock.unlock() }
            return stored
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            stored = newValue
        }
    }
}
