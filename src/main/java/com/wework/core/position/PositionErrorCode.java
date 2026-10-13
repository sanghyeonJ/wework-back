package com.wework.core.position;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PositionErrorCode implements ErrorCode {

    // ===== 직급 =====

    // 404: 없는 직급, 다른 회사 직급
    JOB_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "직급을 찾을 수 없습니다."),

    // 409: 같은 회사에 같은 이름의 직급이 이미 있음
    JOB_GRADE_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 직급 이름입니다."),

    // 409: 이 직급을 가진 회원이 있음 (퇴사자 포함)
    JOB_GRADE_IN_USE(HttpStatus.CONFLICT, "사용 중인 직급은 삭제할 수 없습니다."),

    // ===== 직책 =====

    // 404: 없는 직책, 다른 회사 직책
    JOB_TITLE_NOT_FOUND(HttpStatus.NOT_FOUND, "직책을 찾을 수 없습니다."),

    // 409: 같은 회사에 같은 이름의 직책이 이미 있음
    JOB_TITLE_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 직책 이름입니다."),

    // 409: 이 직책을 가진 회원이 있음 (퇴사자 포함)
    JOB_TITLE_IN_USE(HttpStatus.CONFLICT, "사용 중인 직책은 삭제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
