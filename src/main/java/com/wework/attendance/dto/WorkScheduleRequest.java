package com.wework.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

// 회사 일괄·개인 설정 공용 요청
@Getter
@NoArgsConstructor
public class WorkScheduleRequest {

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    @Min(0)
    @Max(60)
    private Integer graceMinutes;

}
