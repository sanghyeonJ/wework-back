package com.wework.core.leave.service;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.event.ApprovalCompletedEvent;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveRequestRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LeaveApprovalListener {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @EventListener
    @Transactional
    public void handle(ApprovalCompletedEvent event) {
        if (!LeaveRequestService.APPROVAL_TARGET_TYPE.equals(event.getTargetType())) {
            return;
        }

        LeaveRequest leave = leaveRequestRepository.findByIdAndCompanyId(event.getTargetId(), event.getCompanyId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        LeaveRequestStatus before = leave.getStatus();
        boolean deductible = leave.getLeaveType().isDeductible();
        if (event.getResult() == ApprovalRequestStatus.APPROVED) {
            leave.approve();
        } else if (event.getResult() == ApprovalRequestStatus.REJECTED) {
            leave.reject();
        } else if (event.getResult() == ApprovalRequestStatus.CANCELED) {
            leave.cancel();
        }

        if (deductible && shouldUseDays(event.getResult()) && before == LeaveRequestStatus.PENDING) {
            findBalance(leave).useDays(leave.getDays());
        }
        if (deductible && shouldRestoreDays(event.getResult(), event.getPreviousStatus()) && before == LeaveRequestStatus.APPROVED) {
            findBalance(leave).restoreDays(leave.getDays());
        }
    }

    static boolean shouldUseDays(ApprovalRequestStatus result) {
        return result == ApprovalRequestStatus.APPROVED;
    }

    static boolean shouldRestoreDays(ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        if (result == ApprovalRequestStatus.CANCELED && previousStatus == ApprovalRequestStatus.APPROVED) {
            return true;
        }
        return false;
    }

    private LeaveBalance findBalance(LeaveRequest leave) {
        return leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(leave.getCompany().getId(), leave.getMember().getId(), leave.getStartDate().getYear())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }

}
