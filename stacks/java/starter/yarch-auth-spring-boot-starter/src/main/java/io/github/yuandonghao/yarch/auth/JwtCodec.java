package io.github.yuandonghao.yarch.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * JWT 机制件（HS256 默认实现；RS/ES 经 AccessTokenVerifier SPI 扩展——契约"非对称优先"由业务侧密钥体系承接）。 签发：subject + roles +
 * TTL；解析失败与过期分别对应 2001/2002。
 */
public class JwtCodec {

    public static final String CLAIM_ROLES = "roles";

    private final byte[] secret;

    public JwtCodec(String secret) {
        // HS256 要求密钥 ≥32 字节：对配置 secret 做 SHA-256 派生，规避长度陷阱
        try {
            this.secret =
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String issue(String subject, List<String> roles, Duration ttl) {
        try {
            JWSSigner signer = new MACSigner(secret);
            Date now = new Date();
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject(subject)
                            .claim(CLAIM_ROLES, roles)
                            .issueTime(now)
                            .expirationTime(new Date(now.getTime() + ttl.toMillis()))
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("jwt issue failed", e);
        }
    }

    /** 验签 + 未过期校验；无效抛 IllegalArgumentException（2001），过期抛 ExpiredTokenException（2002） */
    public JWTClaimsSet parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new IllegalArgumentException("bad signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() != null
                    && claims.getExpirationTime().before(new Date())) {
                throw new ExpiredTokenException();
            }
            return claims;
        } catch (ExpiredTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("bad token");
        }
    }

    public static class ExpiredTokenException extends RuntimeException {}
}
