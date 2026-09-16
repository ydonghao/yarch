package io.github.ydonghao.{{appPackageSegment}}.core.network.api

import io.github.ydonghao.yarch.client.model.Page
import io.github.ydonghao.yarch.client.model.RestResponse
import javax.inject.Named
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ydonghao.{{appPackageSegment}}.core.network.auth.TokenStore
import io.github.ydonghao.yarch.client.http.RefreshAuthenticator
import io.github.ydonghao.yarch.client.http.YarchHttp
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class UserDto(
    val id: String,
    val name: String,
)

interface SampleApi {

    @GET("api/v1/users")
    suspend fun users(@Query("page") page: Int): retrofit2.Response<RestResponse<Page<UserDto>>>
}

/**
 * 网络装配唯一入口（contract/clients/android.md 二-4/三）：
 * 超时 / trace 注入 / 401 单点刷新 / 信封解码全部收口在契约内核 YarchHttp。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(tokenStore: TokenStore): OkHttpClient =
        YarchHttp.okHttp(
            tokens = { tokenStore.current() },
            authenticator = RefreshAuthenticator { tokenStore.refresh() },
        )

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, @Named("apiBaseUrl") baseUrl: String): Retrofit =
        YarchHttp.retrofit(baseUrl, client)

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): SampleApi = retrofit.create(SampleApi::class.java)
}
