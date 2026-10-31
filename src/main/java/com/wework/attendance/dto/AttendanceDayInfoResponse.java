package com.wework.attendance.dto;

import com.wework.core.holiday.dto.HolidayResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class AttendanceDayInfoResponse {

    private final LocalDate date;
    private final boolean weekend;
    private final List<HolidayResponse> holidays;

    private AttendanceDayInfoResponse(LocalDate date, boolean weekend, List<HolidayResponse> holidays) {
        this.date = date;
        this.weekend = weekend;
        this.holidays = holidays;
    }

    public static AttendanceDayInfoResponse of(LocalDate date, boolean weekend, List<HolidayResponse> holidays) {
        return new AttendanceDayInfoResponse(date, weekend, holidays);
    }

}
