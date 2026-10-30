package com.wework.core.holiday.client;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class PublicHolidayItem {

    private final LocalDate date;
    private final String name;

    private PublicHolidayItem(LocalDate date, String name) {
        this.date = date;
        this.name = name;
    }

    public static PublicHolidayItem of(LocalDate date, String name) {
        return new PublicHolidayItem(date, name);
    }

}
