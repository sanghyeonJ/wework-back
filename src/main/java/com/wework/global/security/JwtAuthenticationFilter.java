package com.wework.global.security;

import com.wework.core.auth.AuthErrorCode;
import com.wework.core.member.MemberRole;
import com.wework.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "jwtError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Bean
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        if(token != null) {
            try {
                Claims claims = jwtProvider.parse(token);
                LoginMember loginMember = toLoginMember(claims);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        loginMember,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + loginMember.getRole().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch(ExpiredJwtException e) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, AuthErrorCode.TOKEN_EXPIRED);
            } catch(JwtException | IllegalArgumentException e) {
                request.setAttribute(JWT_ERROR_ATTRIBUTE, AuthErrorCode.INVALID_TOKEN);
            }
        }

        filterChain.doFilter(request, response);
    }

    // "Bearer eyJhbGci..." 에서 토큰 부분만 꺼내기
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    // 토큰 내용 → LoginMember
    private LoginMember toLoginMember(Claims claims) {
        Long memberId = Long.valueOf(claims.getSubject());
        Long companyId = claims.get(JwtProvider.CLAIM_COMPANY_ID, Long.class);
        MemberRole role = MemberRole.valueOf(claims.get(JwtProvider.CLAIM_ROLE, String.class));
        return new LoginMember(memberId, companyId, role);
    }

}
