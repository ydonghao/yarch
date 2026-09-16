import Testing
import ContractKit
@testable import Users

@MainActor
struct UsersViewModelTests {

    private struct FakeRepository: UsersRepository {
        let result: Result<[User], any Error>

        func users(page: Int) async throws -> [User] {
            switch result {
            case .success(let users): return users
            case .failure(let error): throw error
            }
        }
    }

    @Test("成功态加载用户列表")
    func loadsUsers() async {
        let vm = UsersViewModel(
            repository: FakeRepository(result: .success([User(id: "u1", name: "甲")]))
        )
        await vm.refresh()
        #expect(vm.state.users.count == 1)
        #expect(vm.state.users.first?.id == "u1")
        #expect(vm.state.errorMessage == nil)
        #expect(vm.state.loading == false)
    }

    @Test("业务错误透出 message")
    func apiErrorSurfacesMessage() async {
        let vm = UsersViewModel(
            repository: FakeRepository(
                result: .failure(ApiError(code: 1001, message: "参数校验失败", traceId: "t", httpStatus: 400))
            )
        )
        await vm.refresh()
        #expect(vm.state.users.isEmpty)
        #expect(vm.state.errorMessage == "参数校验失败")
        #expect(vm.state.loading == false)
    }

    @Test("传输错误统一网络文案")
    func transportErrorUsesUnifiedMessage() async {
        let vm = UsersViewModel(
            repository: FakeRepository(result: .failure(NetworkError(reason: "offline")))
        )
        await vm.refresh()
        #expect(vm.state.errorMessage == "网络异常，请稍后重试")
    }
}
