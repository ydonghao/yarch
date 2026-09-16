package io.github.ydonghao.{{appPackageSegment}}.core.network.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 凭证存取端口（client-shared 一-6）：登录态持有与刷新收敛在认证模块单点，
 * 业务模块经注入的取凭证端口拿 token。模板用内存实现占位，接真实认证后端时替换本实现（接口不动）。
 */
interface TokenStore {
    fun current(): String?

    /** 刷新成功返回新 token（重放携带），失败返回 null（落终态 ApiError，跳转登录归认证模块）。 */
    fun refresh(): String?
}

@Singleton
class MemoryTokenStore @Inject constructor() : TokenStore {
    private var token: String? = null

    override fun current(): String? = token

    override fun refresh(): String? {
        // TODO 接入真实认证：调刷新端点持久化；模板内演示为直接放行失败
        return null
    }

    fun update(newToken: String) {
        token = newToken
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TokenStoreModule {

    @Binds
    abstract fun bindTokenStore(impl: MemoryTokenStore): TokenStore
}
