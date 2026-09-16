package io.github.ydonghao.yarch.client.http

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient

/**
 * 全 App 唯一超时配置点（client-shared 三-1）：业务模块禁私自改小改大；
 * 长请求（上传等）例外走显式 override 并登记。默认连接 10s / 读 30s / 写 30s。
 */
public data class HttpConfig(
    public val connectTimeout: Duration = 10.seconds,
    public val readTimeout: Duration = 30.seconds,
    public val writeTimeout: Duration = 30.seconds,
) {
    public fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .connectTimeout(connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}
