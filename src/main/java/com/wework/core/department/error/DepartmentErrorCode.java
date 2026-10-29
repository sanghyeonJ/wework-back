package com.wework.core.department.error;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DepartmentErrorCode implements ErrorCode {

    // 404: 없는 부서, 다른 회사 부서, 삭제(INACTIVE)된 부서
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."),

    // 409: 같은 회사에 같은 이름의 ACTIVE 부서가 이미 있음
    DEPARTMENT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 부서 이름입니다."),

    // 409: 최상위 부서는 회사당 1개 (ACTIVE 기준)
    ROOT_DEPARTMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "최상위 부서가 이미 있습니다."),

    // 400: 최상위 부서는 다른 부서 아래로 옮길 수 없음
    ROOT_DEPARTMENT_CANNOT_MOVE(HttpStatus.BAD_REQUEST, "최상위 부서는 이동할 수 없습니다."),

    // 400: 자기 자신이나 자기 하위 부서를 상위 부서로 지정
    CIRCULAR_DEPARTMENT(HttpStatus.BAD_REQUEST, "자기 자신이나 하위 부서를 상위 부서로 지정할 수 없습니다."),

    // 409: 삭제하려는 부서에 ACTIVE 하위 부서가 있음
    DEPARTMENT_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 부서가 있는 부서는 삭제할 수 없습니다."),

    // 409: 삭제하려는 부서에 재직자(INVITED·ACTIVE)가 있음, 퇴사자는 무관
    DEPARTMENT_HAS_MEMBERS(HttpStatus.CONFLICT, "소속 사원이 있는 부서는 삭제할 수 없습니다."),

    // 400: 부서장 조건(같은 회사 + 그 부서 소속 + ACTIVE)을 만족하지 않음
    INVALID_DEPARTMENT_LEADER(HttpStatus.BAD_REQUEST, "해당 부서에 소속된 재직 중인 사원만 부서장이 될 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}