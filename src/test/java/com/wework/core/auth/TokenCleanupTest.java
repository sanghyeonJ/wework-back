package com.wework.core.auth;

import com.wework.core.auth.entity.MemberToken;
import com.wework.core.auth.entity.MemberTokenType;
import com.wework.core.auth.entity.RefreshToken;
import com.wework.core.auth.repository.MemberTokenRepository;
import com.wework.core.auth.repository.RefreshTokenRepository;
import com.wework.core.auth.scheduler.TokenCleanupScheduler;
import com.wework.core.company.entity.Company;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.core.member.repository.MemberRepository;
import com.wework.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCleanupTest extends IntegrationTestSupport {

    // 리포지토리 테스트는 시각을 직접 넘겨서 시계에 흔들리지 않게 한다
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration GRACE = Duration.ofDays(30);
    private static final Instant CUTOFF = NOW.minus(GRACE);

    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private MemberTokenRepository memberTokenRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private TokenCleanupScheduler tokenCleanupScheduler;

    // 삭제 쿼리는 캐시를 비우지 않으므로, 삭제 뒤 em.clear()로 DB를 직접 보게 한다
    @PersistenceContext
    private EntityManager em;

    private Member member;

    @BeforeEach
    void prepareData() {
        Company company = createCompany("토큰정리", "9970000001");
        member = createMember(company, null, "token-cleanup@test.com", MemberRole.EMPLOYEE);
    }

    // ---------- 도우미 ----------

    // token_hash는 64자 유일값
    private static String hash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private RefreshToken refresh(Instant expiresAt) {
        return refreshTokenRepository.save(RefreshToken.create(member, hash(), expiresAt));
    }

    private MemberToken memberToken(MemberTokenType type, Instant expiresAt, boolean used) {
        MemberToken token = MemberToken.create(member, type, hash(), expiresAt);
        if (used) {
            token.markUsed(NOW);
        }
        return memberTokenRepository.save(token);
    }

    private boolean exists(RefreshToken token) {
        return refreshTokenRepository.existsById(token.getId());
    }

    private boolean exists(MemberToken token) {
        return memberTokenRepository.existsById(token.getId());
    }

    // ================= 리프레시 토큰 =================

    @Test
    @DisplayName("리프레시: 만료된 토큰은 지우고 아직 유효한 토큰은 남긴다")
    void refreshDeletesExpiredOnly() {
        RefreshToken expired = refresh(NOW.minus(Duration.ofDays(1)));
        RefreshToken valid = refresh(NOW.plus(Duration.ofDays(1)));

        int deleted = refreshTokenRepository.deleteExpired(NOW);
        em.clear();

        assertEquals(1, deleted);
        assertFalse(exists(expired));
        assertTrue(exists(valid));
    }

    @Test
    @DisplayName("리프레시: 1초 전에 만료된 것은 지우고, 정확히 지금 만료되는 것은 남긴다 (경계 쌍)")
    void refreshBoundary() {
        RefreshToken oneSecondAgo = refresh(NOW.minusSeconds(1));
        RefreshToken exactlyNow = refresh(NOW);

        int deleted = refreshTokenRepository.deleteExpired(NOW);
        em.clear();

        assertEquals(1, deleted);
        assertFalse(exists(oneSecondAgo));
        assertTrue(exists(exactlyNow));
    }

    @Test
    @DisplayName("리프레시: 지울 것이 없으면 0을 돌려준다")
    void refreshNothingToDelete() {
        RefreshToken valid = refresh(NOW.plus(Duration.ofDays(1)));

        int deleted = refreshTokenRepository.deleteExpired(NOW);
        em.clear();

        assertEquals(0, deleted);
        assertTrue(exists(valid));
    }

    // ================= 초대·재설정 토큰 =================

    @Test
    @DisplayName("초대·재설정: 만료 후 5일(유예 안)은 남기고 31일 지난 것은 지운다")
    void memberTokenKeepsWithinGrace() {
        MemberToken fiveDaysAgo = memberToken(MemberTokenType.INVITE, NOW.minus(Duration.ofDays(5)), false);
        MemberToken thirtyOneDaysAgo = memberToken(MemberTokenType.INVITE, NOW.minus(Duration.ofDays(31)), false);

        int deleted = memberTokenRepository.deleteExpiredBefore(CUTOFF);
        em.clear();

        assertEquals(1, deleted);
        assertTrue(exists(fiveDaysAgo));
        assertFalse(exists(thirtyOneDaysAgo));
    }

    @Test
    @DisplayName("초대·재설정: 사용한 토큰도 같은 기준(만료 시각)으로 지운다")
    void memberTokenUsedFollowsSameRule() {
        MemberToken usedLongExpired = memberToken(MemberTokenType.INVITE, NOW.minus(Duration.ofDays(31)), true);
        MemberToken usedStillValid = memberToken(MemberTokenType.INVITE, NOW.plus(Duration.ofDays(1)), true);

        int deleted = memberTokenRepository.deleteExpiredBefore(CUTOFF);
        em.clear();

        assertEquals(1, deleted);
        assertFalse(exists(usedLongExpired));
        assertTrue(exists(usedStillValid));
    }

    @Test
    @DisplayName("초대·재설정: 정확히 유예 기준 시각에 만료된 것은 남기고 1초 전은 지운다 (경계 쌍)")
    void memberTokenBoundary() {
        MemberToken oneSecondBeforeCutoff = memberToken(MemberTokenType.INVITE, CUTOFF.minusSeconds(1), false);
        MemberToken exactlyCutoff = memberToken(MemberTokenType.INVITE, CUTOFF, false);

        int deleted = memberTokenRepository.deleteExpiredBefore(CUTOFF);
        em.clear();

        assertEquals(1, deleted);
        assertFalse(exists(oneSecondBeforeCutoff));
        assertTrue(exists(exactlyCutoff));
    }

    @Test
    @DisplayName("초대·재설정: 토큰 종류와 상관없이 같은 기준으로 지운다")
    void memberTokenAllTypes() {
        MemberToken invite = memberToken(MemberTokenType.INVITE, NOW.minus(Duration.ofDays(31)), false);
        MemberToken reset = memberToken(MemberTokenType.PASSWORD_RESET, NOW.minus(Duration.ofDays(31)), false);

        int deleted = memberTokenRepository.deleteExpiredBefore(CUTOFF);
        em.clear();

        assertEquals(2, deleted);
        assertFalse(exists(invite));
        assertFalse(exists(reset));
    }

    @Test
    @DisplayName("토큰을 지워도 회원은 남는다")
    void memberSurvives() {
        refresh(NOW.minus(Duration.ofDays(1)));
        memberToken(MemberTokenType.INVITE, NOW.minus(Duration.ofDays(31)), false);

        refreshTokenRepository.deleteExpired(NOW);
        memberTokenRepository.deleteExpiredBefore(CUTOFF);
        em.clear();

        assertTrue(memberRepository.existsById(member.getId()));
    }

    // ================= @Transactional 확인 =================

    // 이 테스트 클래스는 통째로 트랜잭션 안에서 돌아서, 리포지토리의 @Transactional을 빼먹어도
    // 다른 테스트는 초록색이 된다. 스케줄러는 트랜잭션 없이 부르므로 어노테이션이 붙어 있는지 직접 확인한다.
    @Test
    @DisplayName("삭제 쿼리 메서드 2개에 @Modifying과 @Transactional이 붙어 있다")
    void deleteMethodsHaveTransactional() throws NoSuchMethodException {
        Method refreshDelete = RefreshTokenRepository.class.getMethod("deleteExpired", Instant.class);
        Method memberDelete = MemberTokenRepository.class.getMethod("deleteExpiredBefore", Instant.class);

        assertTrue(refreshDelete.isAnnotationPresent(Modifying.class));
        assertTrue(refreshDelete.isAnnotationPresent(Transactional.class));
        assertTrue(memberDelete.isAnnotationPresent(Modifying.class));
        assertTrue(memberDelete.isAnnotationPresent(Transactional.class));
    }

    // ================= 스케줄러 (진짜 현재 시각) =================

    @Test
    @DisplayName("스케줄러: 리프레시는 만료 즉시, 초대·재설정은 30일 뒤에 지운다")
    void schedulerDeletesByEachRule() {
        Instant now = Instant.now();
        RefreshToken expiredRefresh = refresh(now.minus(Duration.ofDays(1)));
        RefreshToken validRefresh = refresh(now.plus(Duration.ofDays(1)));
        MemberToken recentlyExpired = memberToken(MemberTokenType.INVITE, now.minus(Duration.ofDays(5)), false);
        MemberToken longExpired = memberToken(MemberTokenType.INVITE, now.minus(Duration.ofDays(31)), false);

        tokenCleanupScheduler.cleanUpTokens();
        em.clear();

        assertFalse(exists(expiredRefresh));
        assertTrue(exists(validRefresh));
        assertTrue(exists(recentlyExpired));
        assertFalse(exists(longExpired));
    }

    @Test
    @DisplayName("스케줄러: 지울 것이 없어도 예외 없이 끝나고 유효한 토큰은 그대로다")
    void schedulerNothingToDelete() {
        Instant now = Instant.now();
        RefreshToken validRefresh = refresh(now.plus(Duration.ofDays(1)));
        MemberToken validInvite = memberToken(MemberTokenType.INVITE, now.plus(Duration.ofHours(10)), false);

        assertDoesNotThrow(() -> tokenCleanupScheduler.cleanUpTokens());
        em.clear();

        assertTrue(exists(validRefresh));
        assertTrue(exists(validInvite));
    }
}