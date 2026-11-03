package com.wework.core.approval.entity;

public enum ApprovalStepStatus {

    WAITING,   // 아직 내 차례 아님 (앞 단계가 처리되길 기다리는 중)
    PENDING,   // 내 차례 (지금 승인·반려할 수 있음)
    APPROVED,  // 승인함
    REJECTED,  // 반려함
    CANCELED,  // 요청이 반려·취소되어 처리되지 않고 닫힘
    SKIPPED    // 기업관리자의 최종 승인으로 건너뜀

}
