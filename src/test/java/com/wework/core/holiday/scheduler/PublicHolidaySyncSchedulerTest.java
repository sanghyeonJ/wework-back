package com.wework.core.holiday.scheduler;

import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.service.PublicHolidaySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PublicHolidaySyncSchedulerTest {

    private PublicHolidaySyncService service;
    private PublicHolidaySyncScheduler scheduler;
    private int thisYear;

    @BeforeEach
    void setUp() {
        service = mock(PublicHolidaySyncService.class);
        scheduler = new PublicHolidaySyncScheduler(service);
        // 스케줄러와 같은 기준(서울)으로 올해를 구한다
        thisYear = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    }

    // 값을 지정하지 않은 가짜 응답: getAddedCount()는 0을 돌려준다
    private static PublicHolidaySyncResponse okResponse() {
        return mock(PublicHolidaySyncResponse.class);
    }

    @Test
    @DisplayName("올해와 내년을 KR로 한 번씩 불러온다")
    void syncsThisYearAndNextYear() {
        when(service.sync(eq("KR"), anyInt())).thenReturn(okResponse());

        scheduler.syncPublicHolidays();

        verify(service).sync("KR", thisYear);
        verify(service).sync("KR", thisYear + 1);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("올해가 실패해도 예외가 밖으로 나가지 않고 내년은 계속 불러온다")
    void keepsGoingWhenThisYearFails() {
        when(service.sync("KR", thisYear)).thenThrow(new RuntimeException("boom"));
        when(service.sync("KR", thisYear + 1)).thenReturn(okResponse());

        assertDoesNotThrow(() -> scheduler.syncPublicHolidays());

        verify(service).sync("KR", thisYear + 1);
    }

    @Test
    @DisplayName("내년이 실패해도 예외가 밖으로 나가지 않는다")
    void doesNotThrowWhenNextYearFails() {
        when(service.sync("KR", thisYear)).thenReturn(okResponse());
        when(service.sync("KR", thisYear + 1)).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> scheduler.syncPublicHolidays());

        verify(service).sync("KR", thisYear);
    }

    @Test
    @DisplayName("두 해 모두 실패해도 예외가 밖으로 나가지 않고 두 번 시도한다")
    void doesNotThrowWhenBothFail() {
        when(service.sync(eq("KR"), anyInt())).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> scheduler.syncPublicHolidays());

        verify(service, times(2)).sync(eq("KR"), anyInt());
    }

    @Test
    @DisplayName("매일 새벽 3시, 서울 시간대로 실행되도록 설정되어 있다")
    void scheduledAtThreeAmSeoul() throws NoSuchMethodException {
        Scheduled scheduled = PublicHolidaySyncScheduler.class
                .getMethod("syncPublicHolidays")
                .getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 0 3 * * *", scheduled.cron());
        // zone이 빠지면 UTC 서버에서 서울 낮 12시에 돈다
        assertEquals("Asia/Seoul", scheduled.zone());
    }
}