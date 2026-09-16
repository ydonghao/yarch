package io.github.ydonghao.{{appPackageSegment}}.feature.users

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ydonghao.{{appPackageSegment}}.core.network.api.SampleApi
import io.github.ydonghao.{{appPackageSegment}}.core.network.api.UserDto
import io.github.ydonghao.yarch.client.http.Envelope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository = 数据唯一来源（contract/clients/android.md 二-4）：
 * 解包只经契约内核 Envelope.call，业务代码禁手解信封；接口化供 ViewModel 单测替换。
 */
interface UsersRepository {
    suspend fun users(page: Int): List<UserDto>
}

@Singleton
class ApiUsersRepository @Inject constructor(
    private val api: SampleApi,
) : UsersRepository {
    override suspend fun users(page: Int): List<UserDto> =
        Envelope.call { api.users(page) }?.list ?: emptyList()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UsersModule {

    @Binds
    abstract fun bindUsersRepository(impl: ApiUsersRepository): UsersRepository
}
