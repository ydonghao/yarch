import SwiftUI

public struct UsersView: View {
    @StateObject private var viewModel: UsersViewModel

    public init(viewModel: UsersViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
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
                // ContentUnavailableView 为 iOS 17+，本档用同构回退布局
                VStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.title2)
                    Text("加载失败")
                        .font(.headline)
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Button("重试") {
                        Task { await viewModel.refresh() }
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding()
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
