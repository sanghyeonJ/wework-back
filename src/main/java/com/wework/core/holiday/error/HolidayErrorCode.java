package com.wework.core.holiday.error;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HolidayErrorCode implements ErrorCode {

    HOLIDAY_NOT_FOUND(HttpStatus.NOT_FOUND, "휴일을 찾을 수 없습니다."),
    HOLIDAY_DATE_DUPLICATED(HttpStatus.CONFLICT, "이미 휴일로 등록된 날짜입니다."),
    UNSUPPORTED_HOLIDAY_COUNTRY(HttpStatus.BAD_REQUEST, "공휴일을 불러올 수 없는 국가입니다."),
    INVALID_HOLIDAY_YEAR(HttpStatus.BAD_REQUEST, "불러올 수 없는 연도입니다."),
    HOLIDAY_API_KEY_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "공휴일 API 서비스키가 설정되지 않았습니다."),
    HOLIDAY_API_FAILED(HttpStatus.BAD_GATEWAY, "공휴일 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}