import Foundation
import Observation

/**
 * MVVM + @Observable（M6 拍板；contract/clients/ios.md 二-2 ios17 档正式口径）：
 * ViewModel 持全部可变状态（禁 import SwiftUI，可测性前提）；View 只发意图。
 * 登录成功是状态迁移（loggedIn），导航由 View 消费该迁移一次性触发。
 */
@MainActor
public final class LoginViewModel: Observable {
    public struct State: Sendable, Equatable {
        public var username = ""
        public var password = ""
        public var loading = false
        public var errorMessage: String?
        public var loggedIn = false

        public init() {}
    }

    // setter 开放给 SwiftUI Binding 写路径（@Bindable 投影）；写语义仍收敛于 VM 的意图方法，
    // View 不直接改 loading/loggedIn 等流程字段
    public var state = State()

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
