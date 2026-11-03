package com.wework.core.approval.event;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import lombok.Getter;

@Getter
public class ApprovalCompletedEvent {

    private final Long requestId;
    private final Long companyId;
    private final String targetType;
    private final Long targetId;
    private final ApprovalRequestStatus result;
    private final ApprovalRequestStatus previousStatus;

    private ApprovalCompletedEvent(Long requestId, Long companyId, String targetType, Long targetId, ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        this.requestId = requestId;
        this.companyId = companyId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.previousStatus = previousStatus;
    }

    public static ApprovalCompletedEvent of(Long requestId, Long companyId, String targetType, Long targetId, ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        return new ApprovalCompletedEvent(requestId, companyId, targetType, targetId, result, previousStatus);
    }

}
