package com.wework.core.leave.service;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class LeaveApprovalListener {

    static boolean shouldUseDays(ApprovalRequestStatus result) {
        return result == ApprovalRequestStatus.APPROVED;
    }

    static boolean shouldRestoreDays(ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        if (result == ApprovalRequestStatus.CANCELED && previousStatus == ApprovalRequestStatus.APPROVED) {
            return true;
        }
        return false;
    }

}
