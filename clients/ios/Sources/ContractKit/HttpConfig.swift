import Foundation

/// 全 App 唯一超时配置点（client-shared 三-1）：业务模块禁私自改小改大。
/// 默认连接 10s / 读 30s / 写 30s。
/// 平台映射说明：URLSession 不区分连接/读超时，请求级超时 = connect + read 预算之和。
public struct HttpConfig: Sendable {
    public var connectTimeout: TimeInterval
    public var readTimeout: TimeInterval
    public var writeTimeout: TimeInterval

    public init(
        connectTimeout: TimeInterval = 10,
        readTimeout: TimeInterval = 30,
        writeTimeout: TimeInterval = 30
    ) {
        self.connectTimeout = connectTimeout
        self.readTimeout = readTimeout
        self.writeTimeout = writeTimeout
    }

    public var requestTimeout: TimeInterval {
        connectTimeout + readTimeout
    }
}
