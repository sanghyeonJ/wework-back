package com.wework.core.auth.repository;

import com.wework.core.auth.entity.MemberToken;
import com.wework.core.auth.entity.MemberTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberTokenRepository extends JpaRepository<MemberToken, Long> {

    // 링크로 들어온 토큰 찾기 (원문을 해시로 바꿔서 조회)
    Optional<MemberToken> findByTokenHash(String tokenHash);

    // 재발급 시 아직 안 쓴 이전 토큰들 찾기 → 무효화
    List<MemberToken> findAllByMemberIdAndTypeAndUsedAtIsNull(Long memberId, MemberTokenType type);

    // 만료 후 일정 기간이 지난 것만 지움
    @Modifying
    @Transactional
    @Query("delete from MemberToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);


}
