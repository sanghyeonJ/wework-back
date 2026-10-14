package com.wework.core.auth;

import com.wework.core.auth.dto.LoginRequest;
import com.wework.core.auth.dto.RefreshTokenRequest;
import com.wework.core.auth.dto.TokenResponse;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberStatus;
import com.wework.global.error.BusinessException;
import com.wework.global.jwt.JwtProvider;
import com.wework.global.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Value("${jwt.refresh-token-days}")
    private long refreshTokenDays;

    // 로그인
    public TokenResponse login(LoginRequest request) {
        String email = Member.normalizeEmail(request.getEmail());

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if(member.getStatus() != MemberStatus.ACTIVE
                || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(member);
    }

    // 토큰 재발급 (리프레시 토큰 회전)
    public TokenResponse refresh(RefreshTokenRequest request) {
        String tokenHash = TokenHasher.hash(request.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        Member member = refreshToken.getMember();

        if (refreshToken.isExpired(Instant.now()) || member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        refreshTokenRepository.delete(refreshToken);
        return issueTokens(member);
    }

    // 로그아웃 (이 기기의 리프레시 토큰만 삭제)
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.deleteByTokenHash(TokenHasher.hash(request.getRefreshToken()));
    }

    // 액세스 토큰 + 리프레시 토큰 발급
    private TokenResponse issueTokens(Member member) {
        Long companyId = (member.getCompany() != null) ? member.getCompany().getId() : null;
        String accessToken = jwtProvider.createAccessToken(member.getId(), companyId, member.getRole());

        String rawRefreshToken = TokenHasher.generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenDays));
        refreshTokenRepository.save(
                RefreshToken.create(member, TokenHasher.hash(rawRefreshToken), expiresAt)
        );

        return TokenResponse.of(accessToken, rawRefreshToken);
    }

}
