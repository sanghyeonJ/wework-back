package com.wework.core.leave.error;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LeaveErrorCode implements ErrorCode {

    INVALID_LEAVE_DAYS(HttpStatus.BAD_REQUEST, "연차 일수는 0 이상 100 이하, 0.5 단위로 입력해 주세요."),
    INVALID_LEAVE_YEAR(HttpStatus.BAD_REQUEST, "연도가 올바르지 않습니다."),
    INVALID_LEAVE_PERIOD(HttpStatus.BAD_REQUEST, "휴가 기간이 올바르지 않습니다."),
    GRANTED_DAYS_BELOW_USED(HttpStatus.CONFLICT, "지급 일수는 이미 사용한 일수보다 작을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
