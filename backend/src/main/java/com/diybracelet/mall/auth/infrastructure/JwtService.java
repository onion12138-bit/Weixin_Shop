package com.diybracelet.mall.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 管理员 JWT 的签发与校验服务。
 *
 * <p>令牌使用 HMAC-SHA 密钥签名，密钥至少需要 32 字节。服务只返回令牌主体，
 * 不输出令牌日志，避免访问凭证进入日志系统。</p>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public JwtService(
            @Value("${app.auth.jwt-secret}") String jwtSecret,
            @Value("${app.auth.jwt-ttl}") Duration ttl,
            @Value("${app.auth.jwt-issuer}") String issuer
    ) {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 bytes"
            );
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT ttl must be greater than zero"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
        this.ttl = ttl;
        this.issuer = issuer;
    }

    /**
     * 为管理员签发访问令牌。
     *
     * @param username 管理员登录名
     * @return 紧凑 JWT 字符串
     */
    public String issueAccessToken(String username) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("roles", List.of("ROLE_ADMIN"))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 校验签名、签发方和有效期，并返回令牌主体。
     *
     * @param token 紧凑 JWT 字符串
     * @return 管理员登录名
     */
    public String parseUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}
