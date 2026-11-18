package com.wework.core.holiday.scheduler;

import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.service.PublicHolidaySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicHolidaySyncScheduler {

    private static final String SYNC_COUNTRY_CODE = "KR";
    private static final String SYNC_ZONE_ID = "Asia/Seoul";

    private final PublicHolidaySyncService publicHolidaySyncService;

    @Scheduled(cron = "0 0 3 * * *", zone = SYNC_ZONE_ID)
    public void syncPublicHolidays() {
        int thisYear = LocalDate.now(ZoneId.of(SYNC_ZONE_ID)).getYear();

        for (int year : List.of(thisYear, thisYear + 1)) {
            try {
                PublicHolidaySyncResponse result =
                        publicHolidaySyncService.sync(SYNC_COUNTRY_CODE, year);
                if (result.getAddedCount() > 0) {
                    log.info("공휴일 자동 불러오기: year={}, 새로 추가 {}건", year, result.getAddedCount());
                }
            } catch (Exception e) {
                log.error("공휴일 자동 불러오기 실패: year={}, 원인={}", year, e.getClass().getSimpleName());
            }
        }
    }

}
