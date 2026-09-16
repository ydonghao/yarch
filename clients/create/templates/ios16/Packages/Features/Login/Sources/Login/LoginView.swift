import SwiftUI

public struct LoginView: View {
    let onLoggedIn: () -> Void

    @StateObject private var viewModel: LoginViewModel

    public init(
        onLoggedIn: @escaping () -> Void,
        viewModel: LoginViewModel = LoginViewModel()
    ) {
        self.onLoggedIn = onLoggedIn
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    public var body: some View {
        NavigationStack {
            Form {
                TextField("用户名", text: $viewModel.state.username)
                SecureField("密码", text: $viewModel.state.password)
                if let errorMessage = viewModel.state.errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
                Button {
                    Task { await viewModel.submit() }
                } label: {
                    if viewModel.state.loading {
                        ProgressView()
                    } else {
                        Text("登录")
                    }
                }
                .disabled(viewModel.state.loading)
            }
            .navigationTitle("登录")
            .onChange(of: viewModel.state.loggedIn) { loggedIn in
                if loggedIn {
                    onLoggedIn()
                }
            }
        }
    }
}
