import SwiftUI

public struct UsersView: View {
    @State private var viewModel: UsersViewModel

    public init(viewModel: UsersViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        List(viewModel.state.users) { user in
            VStack(alignment: .leading, spacing: 4) {
                Text(user.name)
                Text(user.id)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .overlay {
            if viewModel.state.loading {
                ProgressView()
            } else if let errorMessage = viewModel.state.errorMessage {
                ContentUnavailableView {
                    Label("加载失败", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button("重试") {
                        Task { await viewModel.refresh() }
                    }
                }
            }
        }
        .navigationTitle("用户")
        .toolbar {
            Button("刷新") {
                Task { await viewModel.refresh() }
            }
        }
        .task {
            await viewModel.refresh()
        }
    }
}
