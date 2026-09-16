import Foundation

public let TRACE_ID_HEADER = "X-Trace-Id"

/// traceId 生成策略（client-shared 二-1：32 位小写 hex）。
public typealias TraceIdProvider = @Sendable () -> String

/// 默认策略：16 随机字节 → 32 位小写 hex（Apple 平台 SystemRandomNumberGenerator 具备系统熵源）。
public func makeTraceId() -> String {
    (0..<16).map { _ in String(format: "%02x", UInt8.random(in: 0...255)) }.joined()
}
