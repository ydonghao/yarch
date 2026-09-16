import Testing
@testable import Login

@MainActor
struct LoginViewModelTests {

    private struct FakeRepository: LoginRepository {
        let result: LoginResult
        func login(username: String, password: String) async -> LoginResult { result }
    }

    @Test("成功登录置 loggedIn 并清错误")
    func successSetsLoggedIn() async {
        let vm = LoginViewModel(
            repository: FakeRepository(result: .success(token: "t"))
        )
        await vm.submit()
        #expect(vm.state.loggedIn == true)
        #expect(vm.state.errorMessage == nil)
        #expect(vm.state.loading == false)
    }

    @Test("失败落错误文案且不置 loggedIn")
    func failureSetsMessage() async {
        let vm = LoginViewModel(
            repository: FakeRepository(result: .failure(message: "用户名或密码不能为空"))
        )
        await vm.submit()
        #expect(vm.state.loggedIn == false)
        #expect(vm.state.errorMessage == "用户名或密码不能为空")
        #expect(vm.state.loading == false)
    }
}
