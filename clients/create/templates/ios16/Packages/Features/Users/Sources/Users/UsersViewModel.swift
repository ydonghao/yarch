import ContractKit
import Foundation

/**
 * MVVM ios16 扩展档（contract/clients/ios.md 二-2 降级表）：ObservableObject + @Published 同构实现。
 * 传输错误统一网络文案（client-shared 一-4），业务错误透出 message；取消原生传导不落错误态。
 */
@MainActor
public final class UsersViewModel: ObservableObject {
    public struct State: Sendable, Equatable {
        public var loading = false
        public var users: [User] = []
        public var errorMessage: String?

        public init() {}
    }

    @Published public var state = State()

    private let repository: any UsersRepository

    public init(repository: any UsersRepository) {
        self.repository = repository
    }

    public func refresh() async {
        guard !state.loading else { return }
        state.loading = true
        state.errorMessage = nil
        do {
            state.users = try await repository.users(page: 1)
        } catch let error as ApiError {
            state.errorMessage = error.message
        } catch is NetworkError {
            state.errorMessage = "网络异常，请稍后重试"
        } catch is CancellationError {
            // 取消不是错误（client-shared 一-4）：随 VM 生命周期静默收尾，不落错误态
            state.loading = false
            return
        } catch {
            state.errorMessage = "发生未知错误"
        }
        state.loading = false
    }
}
