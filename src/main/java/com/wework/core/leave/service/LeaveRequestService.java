package com.wework.core.leave.service;

import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestService {

    private final LeaveDayService leaveDayService;

    public BigDecimal calculateApplyDays(Long companyId, LeaveType leaveType, LocalDate start, LocalDate end) {
        BigDecimal days = leaveDayService.calculateDays(companyId, leaveType, start, end);
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(LeaveErrorCode.NO_WORKING_DAY);
        }

        return days;
    }

    static void validateLeaveType(LeaveType leaveType) {
        if (leaveType == LeaveType.SICK) {
            throw new BusinessException(LeaveErrorCode.LEAVE_TYPE_NOT_SUPPORTED);
        }
    }

    static boolean isOverlapAllowed(LeaveType newType, List<LeaveRequest> overlapping) {
        if (overlapping.isEmpty()) {
            return true;
        }

        if (!newType.isHalfDay()) {
            return false;
        }

        if (overlapping.size() != 1) {
            return false;
        }
        LeaveType existingType = overlapping.get(0).getLeaveType();
        if (!existingType.isHalfDay()) {
            return false;
        }
        if (newType == existingType) {
            return false;
        }
        return true;
    }

}
