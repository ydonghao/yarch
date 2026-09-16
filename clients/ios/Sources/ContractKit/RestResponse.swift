import Foundation

/// yarch 统一响应信封（contract/api/rest-response.md v1.0）：
/// code/message/data/traceId 四字段，任何端不得增删改名；字段必填（缺字段视为非法信封）。
public struct RestResponse<T: Decodable & Sendable>: Decodable, Sendable {
    public let code: Int
    public let message: String
    public let data: T?
    public let traceId: String
}

/// 分页负载（rest-response.md「分页负载形状」）：
/// list 可为空数组不得为 null；nextCursor 缺失或空串表示没有下一页。
public struct Page<T: Decodable & Sendable>: Decodable, Sendable {
    public let list: [T]
    public let total: Int64
    public let page: Int
    public let pageSize: Int
    public let nextCursor: String?

    public var hasNext: Bool {
        !(nextCursor ?? "").isEmpty
    }
}
