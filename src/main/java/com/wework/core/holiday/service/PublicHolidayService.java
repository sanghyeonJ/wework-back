package com.wework.core.holiday.service;

import com.wework.core.holiday.dto.PublicHolidayResponse;
import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicHolidayService {

    private final PublicHolidayRepository publicHolidayRepository;

    public List<PublicHolidayResponse> getHolidays(String countryCode, int year) {
        String country = countryCode.trim().toUpperCase();

        return publicHolidayRepository
                .findAllByCountryCodeAndHolidayDateBetweenOrderByHolidayDateAscIdAsc(
                        country, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)
                )
                .stream()
                .map(PublicHolidayResponse::of)
                .toList();
    }

}
