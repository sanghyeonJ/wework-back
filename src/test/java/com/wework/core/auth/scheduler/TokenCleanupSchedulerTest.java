package com.wework.core.auth.scheduler;

import com.wework.core.auth.repository.MemberTokenRepository;
import com.wework.core.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenCleanupSchedulerTest {

    private RefreshTokenRepository refreshTokenRepository;
    private MemberTokenRepository memberTokenRepository;
    private TokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        memberTokenRepository = mock(MemberTokenRepository.class);
        scheduler = new TokenCleanupScheduler(refreshTokenRepository, memberTokenRepository);
    }

    @Test
    @DisplayName("리프레시 토큰과 초대·재설정 토큰을 둘 다 정리한다")
    void cleansBoth() {
        scheduler.cleanUpTokens();

        verify(refreshTokenRepository).deleteExpired(any(Instant.class));
        verify(memberTokenRepository).deleteExpiredBefore(any(Instant.class));
    }

    @Test
    @DisplayName("리프레시에는 지금을, 초대·재설정에는 지금 − 30일을 넘긴다")
    void passesRightTimes() {
        Instant before = Instant.now();
        scheduler.cleanUpTokens();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> refreshCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> memberCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpired(refreshCaptor.capture());
        verify(memberTokenRepository).deleteExpiredBefore(memberCaptor.capture());

        Instant refreshArg = refreshCaptor.getValue();
        Instant memberArg = memberCaptor.getValue();

        // 리프레시: 이 메서드가 도는 동안의 "지금"
        assertFalse(refreshArg.isBefore(before));
        assertFalse(refreshArg.isAfter(after));
        // 초대·재설정: 같은 "지금"에서 정확히 30일 전 (now를 그대로 넘기면 0일이 되어 빨개진다)
        assertEquals(Duration.ofDays(30), Duration.between(memberArg, refreshArg));
    }

    @Test
    @DisplayName("리프레시 정리가 실패해도 예외가 밖으로 나가지 않고 초대·재설정은 계속 정리한다")
    void keepsGoingWhenRefreshFails() {
        when(refreshTokenRepository.deleteExpired(any(Instant.class)))
                .thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> scheduler.cleanUpTokens());

        verify(memberTokenRepository).deleteExpiredBefore(any(Instant.class));
    }

    @Test
    @DisplayName("초대·재설정 정리가 실패해도 예외가 밖으로 나가지 않는다")
    void doesNotThrowWhenMemberTokenFails() {
        when(memberTokenRepository.deleteExpiredBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> scheduler.cleanUpTokens());

        verify(refreshTokenRepository).deleteExpired(any(Instant.class));
    }

    @Test
    @DisplayName("둘 다 실패해도 예외가 밖으로 나가지 않는다")
    void doesNotThrowWhenBothFail() {
        when(refreshTokenRepository.deleteExpired(any(Instant.class)))
                .thenThrow(new RuntimeException("boom"));
        when(memberTokenRepository.deleteExpiredBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> scheduler.cleanUpTokens());
    }

    @Test
    @DisplayName("매일 새벽 4시, 서울 시간대로 실행되도록 설정되어 있다")
    void scheduledAtFourAmSeoul() throws NoSuchMethodException {
        Scheduled scheduled = TokenCleanupScheduler.class
                .getMethod("cleanUpTokens")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 0 4 * * *", scheduled.cron());
        // zone이 빠지면 UTC 서버에서 서울 오후 1시에 돈다
        assertEquals("Asia/Seoul", scheduled.zone());
    }

    // 클래스에 @Transactional을 붙이면 두 삭제가 한 트랜잭션으로 묶인다.
    // 앞의 것이 실패해 잡아 놓아도 트랜잭션은 이미 "롤백 전용"이 되어 뒤의 것까지 못 쓰게 된다.
    @Test
    @DisplayName("스케줄러 클래스와 메서드에는 @Transactional이 없다")
    void schedulerIsNotTransactional() throws NoSuchMethodException {
        assertFalse(TokenCleanupScheduler.class.isAnnotationPresent(Transactional.class));
        assertFalse(TokenCleanupScheduler.class.getMethod("cleanUpTokens")
                .isAnnotationPresent(Transactional.class));
    }
}