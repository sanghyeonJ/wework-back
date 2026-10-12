package com.wework.global.jwt;

import com.wework.core.member.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    public static final String CLAIM_COMPANY_ID = "companyId";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration accessTokenValidity;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-token-days}") long accessTokenDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = Duration.ofDays(accessTokenDays);
    }

    // 액세스 토큰 만들기
    public String createAccessToken(Long memberId, Long companyId, MemberRole role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_COMPANY_ID, companyId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenValidity)))
                .signWith(key)
                .compact();
    }

    // 토큰 검사 + 내용 꺼내기 (가짜·만료면 JwtException 발생)
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}