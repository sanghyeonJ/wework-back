package com.wework.core.holiday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    // 한 해(또는 기간)의 공휴일 목록. 조회 API와 일수 계산이 사용
    List<PublicHoliday> findAllByCountryCodeAndHolidayDateBetweenOrderByHolidayDateAscIdAsc(
            String countryCode, LocalDate from, LocalDate to
    );

}
