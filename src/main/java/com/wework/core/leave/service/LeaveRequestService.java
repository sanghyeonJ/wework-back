package com.wework.core.leave.service;

import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveRequestRepository;
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
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public BigDecimal calculateApplyDays(Long companyId, LeaveType leaveType, LocalDate start, LocalDate end) {
        BigDecimal days = leaveDayService.calculateDays(companyId, leaveType, start, end);
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(LeaveErrorCode.NO_WORKING_DAY);
        }

        return days;
    }

    private void validateBalance(Long companyId, Long memberId, LocalDate start, BigDecimal applyDays) {
        int year = start.getYear();
        BigDecimal remaining = leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year)
                .map(balance -> LeaveBalanceService.remainingDays(balance.getGrantedDays(), balance.getUsedDays()))
                .orElse(BigDecimal.ZERO);

        BigDecimal pending = leaveBalanceService.pendingDays(companyId, memberId, year);
        if (!isBalanceEnough(remaining, pending, applyDays)) {
            throw new BusinessException(LeaveErrorCode.INSUFFICIENT_LEAVE_BALANCE);
        }
    }

    static boolean isBalanceEnough(BigDecimal remaining, BigDecimal pending, BigDecimal applyDays) {
        BigDecimal available = remaining.subtract(pending);
        return available.compareTo(applyDays) >= 0;
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

    private void validateNoOverlap(Long companyId, Long memberId, LeaveType leaveType, LocalDate start, LocalDate end) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                companyId, memberId, List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED), start, end
        );
        if (!isOverlapAllowed(leaveType, overlapping)) {
            throw new BusinessException(LeaveErrorCode.LEAVE_OVERLAPPED);
        }
    }

}
