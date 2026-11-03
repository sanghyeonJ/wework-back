package com.wework.core.approval.entity;

public enum ApprovalRequestStatus {

    PENDING,   // 결재 진행 중
    APPROVED,  // 최종 승인됨
    REJECTED,  // 반려됨
    CANCELED   // 취소됨

}
