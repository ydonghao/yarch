package io.github.ydonghao.{{appPackageSegment}}.core.common.coroutines

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers 注入载体（contract/clients/android.md 七-2）：
 * 业务逻辑禁硬编码 Dispatchers 切换——统一经本类型注入，单测以测试调度器替换默认值。
 */
@Singleton
class AppDispatchers @Inject constructor(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)
