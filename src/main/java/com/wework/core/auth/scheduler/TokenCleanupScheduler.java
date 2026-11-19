package com.wework.core.auth.scheduler;

import com.wework.core.auth.repository.MemberTokenRepository;
import com.wework.core.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    // 초대·재설정 토큰은 만료 후 이 기간이 지나야 지움
    private static final Duration MEMBER_TOKEN_RETENTION = Duration.ofDays(30);
    private static final String ZONE_ID = "Asia/Seoul";

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberTokenRepository memberTokenRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = ZONE_ID)
    public void cleanUpTokens() {
        Instant now = Instant.now();

        // 리프레시 토큰 삭제
        try {
            int deleted = refreshTokenRepository.deleteExpired(now);
            if (deleted > 0) {
                log.info("만료된 리프레시 토큰 삭제: {}개", deleted);
            }
        } catch (Exception e) {
            log.error("만료된 리프레시 토큰 삭제 실패", e);
        }

        // 초대, 재설정 토큰 삭제
        try {
            Instant cutoff = now.minus(MEMBER_TOKEN_RETENTION);
            int deleted = memberTokenRepository.deleteExpiredBefore(cutoff);
            if (deleted > 0) {
                log.info("만료된 초대 토큰 삭제: {}개", deleted);
            }
        } catch (Exception e) {
            log.error("만료된 초대 토큰 삭제 실패", e);
        }
    }

}
