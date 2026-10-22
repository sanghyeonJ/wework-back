package com.wework.attendance;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EffectiveWorkSchedule {

    private final WorkScheduleSource source;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final int graceMinutes;

    public static EffectiveWorkSchedule of(WorkScheduleSource source, LocalTime startTime, LocalTime endTime, int graceMinutes) {
        return new EffectiveWorkSchedule(source, startTime, endTime, graceMinutes);
    }

}
