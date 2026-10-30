package com.wework.core.holiday.service;

import com.wework.core.holiday.client.PublicHolidayClient;
import com.wework.core.holiday.client.PublicHolidayItem;
import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.error.HolidayErrorCode;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicHolidaySyncService {

    private static final String SUPPORTED_COUNTRY = "KR";
    private static final int MIN_YEAR = 2015;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final PublicHolidayClient publicHolidayClient;
    private final PublicHolidayRepository publicHolidayRepository;

    @Transactional
    public PublicHolidaySyncResponse sync(String countryCode, int year) {
        // 1) 국가 코드 정리 + 지원 국가 확인
        String country = countryCode.trim().toUpperCase();
        if (!country.equals(SUPPORTED_COUNTRY)) {
            throw new BusinessException(HolidayErrorCode.UNSUPPORTED_HOLIDAY_COUNTRY);
        }

        // 2) 연도 범위 확인 (공공 API를 12번 부르기 전에 막음)
        int maxYear = LocalDate.now(KOREA).getYear() + 1;
        if (year < MIN_YEAR || year > maxYear) {
            throw new BusinessException(HolidayErrorCode.INVALID_HOLIDAY_YEAR);
        }

        // 3) 공공 API에서 한 해 전체 받기
        List<PublicHolidayItem> fetched = publicHolidayClient.fetchYear(year);

        // 4) 이미 저장된 그해 공휴일을 한 번에 조회 → (날짜+이름) 키 Set
        List<PublicHoliday> existing = publicHolidayRepository
                .findAllByCountryCodeAndHolidayDateBetweenOrderByHolidayDateAscIdAsc(
                        country, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)
                );
        Set<String> keys = new HashSet<>();
        for (PublicHoliday holiday : existing) {
            keys.add(toKey(holiday.getHolidayDate(), holiday.getName()));
        }

        // 5) 키가 Set에 없는 것만 새로 저장할 목록에 담기
        List<PublicHoliday> toSave = new ArrayList<>();
        for (PublicHolidayItem item : fetched) {
            if (keys.add(toKey(item.getDate(), item.getName()))) {
                toSave.add(PublicHoliday.create(country, item.getDate(), item.getName()));
            }
        }
        publicHolidayRepository.saveAll(toSave);

        return PublicHolidaySyncResponse.of(country, year, fetched.size(), toSave.size());
    }

    private String toKey(LocalDate date, String name) {
        return date + "|" + name;
    }

}
