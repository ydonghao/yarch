package io.github.ydonghao.yarch.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Bearer 解析 + 角色校验：2001（无效/缺失）、2002（过期）、2003（角色不足）——契约 2xxx 段 */
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtCodec jwtCodec;

    public AuthInterceptor(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRoles requireRoles = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (requireRoles == null) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "missing bearer token");
        }
        JWTClaimsSet claims;
        try {
            claims = jwtCodec.parse(authorization.substring("Bearer ".length()).trim());
        } catch (JwtCodec.ExpiredTokenException e) {
            throw new BusinessException(GlobalErrorCode.CREDENTIALS_EXPIRED);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        AuthContext.set(claims.getSubject());
        try {
            List<String> roles = claims.getStringListClaim(JwtCodec.CLAIM_ROLES);
            for (String required : requireRoles.value()) {
                if (roles != null && roles.contains(required)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // claim 缺失按角色不足处理
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        AuthContext.clear();
    }
}
