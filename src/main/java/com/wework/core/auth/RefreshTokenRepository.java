package com.wework.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 해시로 찾기 (재발급)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 해시로 삭제 (로그아웃)
    void deleteByTokenHash(String tokenHash);

    // 회원의 토큰 전부 삭제 (퇴사)
    void deleteAllByMemberId(Long memberId);

}
