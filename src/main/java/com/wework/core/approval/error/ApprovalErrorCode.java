package com.wework.core.approval.error;

import com.wework.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ApprovalErrorCode implements ErrorCode {

    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "결재 요청을 찾을 수 없습니다."),
    INVALID_APPROVER(HttpStatus.BAD_REQUEST, "지정할 수 없는 결재자입니다."),
    INVALID_APPROVER_COUNT(HttpStatus.BAD_REQUEST, "결재자는 1명 이상 5명 이하로 지정해야 합니다."),
    APPROVAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 결재가 요청된 건입니다."),
    NOT_APPROVER(HttpStatus.FORBIDDEN, "이 결재를 처리할 권한이 없습니다."),
    CANNOT_PROCESS_OWN_REQUEST(HttpStatus.FORBIDDEN, "본인이 신청한 건은 승인하거나 반려할 수 없습니다."),
    NOT_YOUR_TURN(HttpStatus.CONFLICT, "지금은 내가 처리할 차례가 아닙니다."),
    APPROVAL_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 결재 건입니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "승인이 완료된 건은 취소할 수 없습니다."),
    CANCEL_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "취소 사유를 입력해야 합니다.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }

}
