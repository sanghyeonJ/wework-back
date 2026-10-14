package com.wework.core.auth;

import com.wework.core.member.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "member_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberTokenType type;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    private MemberToken(Member member, MemberTokenType type, String tokenHash, Instant expiresAt) {
        this.member = member;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static MemberToken create(Member member, MemberTokenType type, String tokenHash, Instant expiresAt) {
        return new MemberToken(member, type, tokenHash, expiresAt);
    }

    // 사용 처리 (초대 수락, 재발급 시 이전 토큰 무효화에 모두 사용)
    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

}
