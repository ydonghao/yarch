import Foundation

/**
 * MVVM ios16 扩展档（contract/clients/ios.md 二-2 降级表）：
 * @Observable 宏要求 iOS 17+，本档以 ObservableObject + @Published 同构实现——
 * 状态形状、意图方法与 ios17 档一致，仅状态容器方言不同。
 */
@MainActor
public final class LoginViewModel: ObservableObject {
    public struct State: Sendable, Equatable {
        public var username = ""
        public var password = ""
        public var loading = false
        public var errorMessage: String?
        public var loggedIn = false

        public init() {}
    }

    // setter 开放给 SwiftUI Binding 写路径；写语义仍收敛于 VM 的意图方法
    @Published public var state = State()

    private let repository: any LoginRepository

    public init(repository: any LoginRepository = MockLoginRepository()) {
        self.repository = repository
    }

    public func onUsernameChange(_ value: String) {
        state.username = value
        state.errorMessage = nil
    }

    public func onPasswordChange(_ value: String) {
        state.password = value
        state.errorMessage = nil
    }

    public func submit() async {
        guard !state.loading else { return }
        state.loading = true
        state.errorMessage = nil
        let result = await repository.login(username: state.username, password: state.password)
        state.loading = false
        switch result {
        case .success:
            state.loggedIn = true
        case .failure(let message):
            state.errorMessage = message
        }
    }
}
