package io.github.ydonghao.yarch.client.http

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * OkHttp + Retrofit 装配单点（模板预接线载体）：
 * 超时 / trace 注入 / 认证 / 信封解码配置在此收口，业务模块禁散装第二套。
 */
public object YarchHttp {

    public fun okHttp(
        config: HttpConfig = HttpConfig(),
        trace: TraceIdProvider = SecureRandomTraceIdProvider,
        tokens: TokenProvider? = null,
        authenticator: Authenticator? = null,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder().addInterceptor(TraceIdInterceptor(trace))
        tokens?.let { builder.addInterceptor(AuthInterceptor(it)) }
        authenticator?.let { builder.authenticator(it) }
        return config.applyTo(builder).build()
    }

    public fun retrofit(baseUrl: String, client: OkHttpClient, json: Json = Envelope.json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
