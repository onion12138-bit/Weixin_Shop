package com.diybracelet.mall.identity.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
 * 顾客侧 JWT 的签发与校验服务。
 *
 * <p>该服务使用独立密钥、签发方和 {@code tokenType=CUSTOMER} 声明，与管理后台
 * 管理员 JWT 完全隔离。这样即使两个接口都使用 Bearer 令牌，管理员 token 也不能访问
 * 小程序顾客接口，顾客 token 也不能访问后台管理接口。</p>
 */
@Service
public class CustomerJwtService {

    private static final String TOKEN_TYPE = "CUSTOMER";

    private final SecretKey signingKey;
    private final Duration ttl;
    private final String issuer;

    public CustomerJwtService(
            @Value("${app.customer-auth.jwt-secret}") String jwtSecret,
            @Value("${app.customer-auth.jwt-ttl}") Duration ttl,
            @Value("${app.customer-auth.jwt-issuer}") String issuer
    ) {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "Customer JWT secret must contain at least 32 bytes"
            );
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "Customer JWT ttl must be greater than zero"
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
        this.ttl = ttl;
        this.issuer = issuer;
    }

    /**
     * 为顾客身份签发访问令牌。
     *
     * @param customerIdentityId 顾客身份主键
     * @return 紧凑 JWT 字符串
     */
    public String issue(Long customerIdentityId) {
        if (customerIdentityId == null || customerIdentityId <= 0) {
            throw new IllegalArgumentException("顾客身份主键必须大于零");
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        return Jwts.builder()
                .subject(customerIdentityId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("tokenType", TOKEN_TYPE)
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 校验顾客令牌并返回顾客身份主键。
     *
     * @param token 紧凑 JWT 字符串
     * @return 顾客身份主键
     */
    public Long parseCustomerIdentityId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String tokenType = claims.get("tokenType", String.class);
        if (!TOKEN_TYPE.equals(tokenType)) {
            throw new JwtException("Invalid customer token type");
        }
        try {
            long customerIdentityId = Long.parseLong(claims.getSubject());
            if (customerIdentityId <= 0) {
                throw new NumberFormatException("non-positive subject");
            }
            return customerIdentityId;
        } catch (NumberFormatException exception) {
            throw new JwtException("Invalid customer subject", exception);
        }
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}
