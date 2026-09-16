package io.github.ydonghao.yarch.client.http

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** 取凭证端口（client-shared 一-6）：业务侧认证模块实现，返回 null 表示匿名请求。 */
public fun interface TokenProvider {
    public fun token(): String?
}

/** Bearer 注入（请求已带 Authorization 头则不覆盖）。 */
public class AuthInterceptor(
    private val tokens: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.token()
        val authorized = if (!request.header("Authorization").isNullOrBlank() || token == null) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(authorized)
    }
}

/**
 * 401 单点处理（client-shared 一-6）：刷新凭证 → 重放原请求，至多一次（priorResponse 防环）；
 * 刷新失败返回 null 放行 401，由解包层落为 ApiError(2001/2002)——跳转登录归认证模块单点。
 */
public class RefreshAuthenticator(
    private val refresh: () -> String?,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) {
            return null
        }
        val newToken = refresh() ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }
}
