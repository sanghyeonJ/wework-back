package com.wework.core.member;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    // 404
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사원을 찾을 수 없습니다."),

    // 400
    INVALID_MEMBER_ROLE(HttpStatus.BAD_REQUEST, "지정할 수 없는 권한입니다."),
    INVALID_RESIGN_DATE(HttpStatus.BAD_REQUEST, "퇴사일은 입사일부터 오늘까지의 날짜만 지정할 수 있습니다."),

    // 409
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    MEMBER_RESIGNED(HttpStatus.CONFLICT, "퇴사한 사원은 변경할 수 없습니다."),
    MEMBER_NOT_INVITED(HttpStatus.CONFLICT, "초대 상태인 사원만 초대를 재발급할 수 있습니다."),
    LEADER_CANNOT_CHANGE_DEPARTMENT(HttpStatus.CONFLICT, "부서장은 부서를 변경할 수 없습니다. 먼저 부서장을 해제하세요."),
    LEADER_CANNOT_RESIGN(HttpStatus.CONFLICT, "부서장은 퇴사 처리할 수 없습니다. 먼저 부서장을 해제하세요."),
    LAST_COMPANY_ADMIN(HttpStatus.CONFLICT, "회사에는 기업관리자가 최소 1명 있어야 합니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
