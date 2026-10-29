package com.wework.core.holiday.dto;

import com.wework.core.holiday.entity.CompanyHoliday;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CompanyHolidayResponse {

    private final Long id;
    private final LocalDate holidayDate;
    private final String name;
    private final boolean recurring;

    public static CompanyHolidayResponse of(CompanyHoliday holiday) {
        return new CompanyHolidayResponse(
                holiday.getId(),
                holiday.getHolidayDate(),
                holiday.getName(),
                holiday.isRecurring()
        );
    }

}
