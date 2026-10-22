package com.wework.attendance;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttendanceErrorCode implements ErrorCode {

    // 출퇴근 (409: 현재 기록 상태와 충돌)
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "오늘 이미 출근했습니다."),
    NOT_CHECKED_IN(HttpStatus.CONFLICT, "오늘 출근 기록이 없습니다."),

    // 근무시간 설정
    INVALID_WORK_TIME(HttpStatus.BAD_REQUEST, "퇴근 기준 시각은 출근 기준 시각보다 늦어야 합니다."),
    WORK_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "근무시간 설정을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode(){
        return name();
    }

}
