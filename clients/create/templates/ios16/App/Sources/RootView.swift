import ContractKit
import Login
import SwiftUI
import Users

struct RootView: View {
    @State private var isLoggedIn = false

    private let client: ApiClient

    init() {
        // 环境注入位（client-shared 三-5）：base URL 来自构建配置（project.yml Info 属性），禁散落业务代码
        let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String ?? ""
        // swiftlint:disable:next force_unwrapping 字面量兜底常量，URL(string:) 对合法字面量恒成功
        let fallback = URL(string: "https://api.example.com/")!
        self.client = ApiClient(baseURL: URL(string: raw) ?? fallback)
    }

    var body: some View {
        if isLoggedIn {
            NavigationStack {
                UsersView(
                    viewModel: UsersViewModel(
                        repository: ApiUsersRepository(client: client)
                    )
                )
            }
        } else {
            LoginView {
                isLoggedIn = true
            }
        }
    }
}
