package com.wework.core.auth.repository;

import com.wework.core.auth.entity.MemberToken;
import com.wework.core.auth.entity.MemberTokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberTokenRepository extends JpaRepository<MemberToken, Long> {

    // 링크로 들어온 토큰 찾기 (원문을 해시로 바꿔서 조회)
    Optional<MemberToken> findByTokenHash(String tokenHash);

    // 재발급 시 아직 안 쓴 이전 토큰들 찾기 → 무효화
    List<MemberToken> findAllByMemberIdAndTypeAndUsedAtIsNull(Long memberId, MemberTokenType type);

}
