package com.wework.core.holiday.dto;

import com.wework.core.holiday.entity.PublicHoliday;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class PublicHolidayResponse {

    private final LocalDate holidayDate;
    private final String name;

    private PublicHolidayResponse(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }

    public static PublicHolidayResponse of(PublicHoliday holiday) {
        return new PublicHolidayResponse(holiday.getHolidayDate(), holiday.getName());
    }

}
