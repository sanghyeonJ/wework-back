package com.wework.core.holiday.dto;

import com.wework.core.holiday.entity.HolidayType;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class HolidayResponse {

    private final LocalDate date;
    private final String name;
    private final HolidayType type;

    private HolidayResponse(LocalDate date, String name, HolidayType type) {
        this.date = date;
        this.name = name;
        this.type = type;
    }

    public static HolidayResponse of(LocalDate date, String name, HolidayType type) {
        return new HolidayResponse(date, name, type);
    }

}
