package com.wework.core.auth;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    // 초대
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다."),
    INVITATION_EXPIRED(HttpStatus.CONFLICT, "만료된 초대 링크입니다. 관리자에게 재발급을 요청하세요."),
    INVITATION_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 초대 링크입니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
