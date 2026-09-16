package io.github.ydonghao.{{appPackageSegment}}.feature.login

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

sealed interface LoginResult {
    data class Success(val token: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}

interface LoginRepository {
    suspend fun login(username: String, password: String): LoginResult
}

/** 模板占位实现：接真实认证后端时替换（接口与 UiState 形状不动）。 */
@Singleton
class MockLoginRepository @Inject constructor() : LoginRepository {
    override suspend fun login(username: String, password: String): LoginResult {
        delay(600)
        return if (username.isBlank() || password.isBlank()) {
            LoginResult.Failure("用户名或密码不能为空")
        } else {
            LoginResult.Success("mock-token")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LoginModule {

    @Binds
    abstract fun bindLoginRepository(impl: MockLoginRepository): LoginRepository
}
