import ContractKit
import Foundation

public struct User: Codable, Sendable, Identifiable, Hashable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

/**
 * Repository = 数据唯一来源（contract/clients/ios.md 二-4）：
 * 解包只经契约内核 ApiClient，业务代码禁手解信封；协议化供 ViewModel 单测替换。
 */
public protocol UsersRepository: Sendable {
    func users(page: Int) async throws -> [User]
}

public struct ApiUsersRepository: UsersRepository {
    private let client: ApiClient

    public init(client: ApiClient) {
        self.client = client
    }

    public func users(page: Int) async throws -> [User] {
        let page: Page<User>? = try await client.get("api/v1/users", query: ["page": String(page)])
        return page?.list ?? []
    }
}
