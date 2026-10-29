package com.wework.attendance.dto;

import com.wework.attendance.entity.Attendance;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AttendanceResponse {

    private final Long id;
    private final LocalDate workDate;
    private final Instant checkInAt;
    private final Instant checkOutAt;
    private final boolean late;
    private final boolean earlyLeave;

    public static AttendanceResponse of(Attendance attendance) {
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getWorkDate(),
                attendance.getCheckInAt(),
                attendance.getCheckOutAt(),
                attendance.isLate(),
                attendance.isEarlyLeave()
        );
    }

}
