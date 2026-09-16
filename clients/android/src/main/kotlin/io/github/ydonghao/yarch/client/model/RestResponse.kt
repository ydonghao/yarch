package io.github.ydonghao.yarch.client.model

import kotlinx.serialization.Serializable

/**
 * yarch 统一响应信封（contract/api/rest-response.md v1.0）：
 * code/message/data/traceId 四字段，任何端不得增删改名。
 */
@Serializable
public data class RestResponse<T>(
    public val code: Int,
    public val message: String,
    public val data: T? = null,
    public val traceId: String = "",
)

/**
 * 分页负载（rest-response.md「分页负载形状」）：
 * list 可为空数组不得为 null；nextCursor 缺失或空串表示没有下一页。
 */
@Serializable
public data class Page<T>(
    public val list: List<T> = emptyList(),
    public val total: Long = 0,
    public val page: Int = 1,
    public val pageSize: Int = 20,
    public val nextCursor: String? = null,
) {
    public val hasNext: Boolean get() = !nextCursor.isNullOrEmpty()
}
