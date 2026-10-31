package com.wework.core.holiday.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.holiday.dto.HolidayResponse;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.entity.HolidayType;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayCalendarService {

    private final CompanyRepository companyRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final CompanyHolidayRepository companyHolidayRepository;

    public List<HolidayResponse> getYearHolidays(Long companyId, int year) {
        return getHolidays(companyId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public List<HolidayResponse> getHolidays(Long companyId, LocalDate from, LocalDate to) {
        List<HolidayResponse> result = new ArrayList<>();

        // 1) 회사의 국가 코드를 알기 위해 회사 조회
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        // 2) 국가 공휴일 (데이터가 없는 나라는 빈 목록)
        List<PublicHoliday> publicHolidays = publicHolidayRepository.findAllByCountryCodeAndHolidayDateBetweenOrderByHolidayDateAscIdAsc(company.getCountryCode(), from, to);
        for (PublicHoliday holiday : publicHolidays) {
            result.add(HolidayResponse.of(holiday.getHolidayDate(), holiday.getName(), HolidayType.PUBLIC));
        }

        // 3) 회사 휴일
        List<CompanyHoliday> companyHolidays = companyHolidayRepository.findAllInRange(companyId, from, to);
        for (CompanyHoliday holiday : companyHolidays) {
            if (!holiday.isRecurring()) {
                result.add(HolidayResponse.of(holiday.getHolidayDate(), holiday.getName(), HolidayType.COMPANY));
                continue;
            }

            // 반복 휴일: 조회 기간에 걸친 각 해의 날짜로 바꿔서 추가
            MonthDay monthDay = MonthDay.from(holiday.getHolidayDate());
            for (int y = from.getYear(); y <= to.getYear(); y++) {
                if (!monthDay.isValidYear(y)) {
                    continue;
                }

                LocalDate date = LocalDate.of(y, holiday.getHolidayDate().getMonth(), holiday.getHolidayDate().getDayOfMonth());
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                result.add(HolidayResponse.of(date, holiday.getName(), HolidayType.COMPANY));
            }
        }

        // 4) 날짜순 정렬
        result.sort(Comparator.comparing(HolidayResponse::getDate)
                .thenComparing(HolidayResponse::getType)
                .thenComparing(HolidayResponse::getName)
        );
        return result;
    }

    // 토·일이면 true. 근태 조회의 주말 표시와 휴가 일수 계산(주말 제외)이 같이 쓰는 규칙
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

}
