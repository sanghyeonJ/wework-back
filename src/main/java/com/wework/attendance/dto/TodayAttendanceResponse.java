package com.wework.attendance.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TodayAttendanceResponse {

    private final LocalDate workDate;
    private final AttendanceResponse attendance;

    public static TodayAttendanceResponse of(LocalDate workDate, AttendanceResponse attendance) {
        return new TodayAttendanceResponse(workDate, attendance);
    }

}
