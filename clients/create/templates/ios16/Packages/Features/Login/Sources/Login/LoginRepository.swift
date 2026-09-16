import Foundation

public enum LoginResult: Sendable, Equatable {
    case success(token: String)
    case failure(message: String)
}

public protocol LoginRepository: Sendable {
    func login(username: String, password: String) async -> LoginResult
}

/// 模板占位实现：接真实认证后端时替换本实现（协议与状态形状不动）。
public struct MockLoginRepository: LoginRepository {
    public init() {}

    public func login(username: String, password: String) async -> LoginResult {
        try? await Task.sleep(for: .milliseconds(600))
        if username.isEmpty || password.isEmpty {
            return .failure(message: "用户名或密码不能为空")
        }
        return .success(token: "mock-token")
    }
}
