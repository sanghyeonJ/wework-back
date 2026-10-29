package com.wework.attendance.dto;

import com.wework.attendance.entity.WorkSchedule;
import com.wework.attendance.entity.WorkScheduleScope;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalTime;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkScheduleResponse {

    private final Long id;
    private final WorkScheduleScope scopeType;
    private final Long memberId;
    private final String memberName;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final int graceMinutes;

    public static WorkScheduleResponse of(WorkSchedule schedule) {
        return new WorkScheduleResponse(
                schedule.getId(),
                schedule.getScopeType(),
                schedule.getMember() != null ? schedule.getMember().getId() : null,
                schedule.getMember() != null ? schedule.getMember().getName() : null,
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getGraceMinutes()
        );
    }

}
