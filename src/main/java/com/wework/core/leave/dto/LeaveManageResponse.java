package com.wework.core.leave.dto;

import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class LeaveManageResponse {

    private final Long id;
    private final Long memberId;
    private final String memberName;
    private final String departmentName;
    private final LeaveType leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal days;
    private final LeaveRequestStatus status;
    private final Long approvalRequestId;

    private LeaveManageResponse(LeaveRequest request) {
        this.id = request.getId();
        this.memberId = request.getMember().getId();
        this.memberName = request.getMember().getName();
        this.departmentName = request.getMember().getDepartment() != null ? request.getMember().getDepartment().getName() : null;
        this.leaveType = request.getLeaveType();
        this.startDate = request.getStartDate();
        this.endDate = request.getEndDate();
        this.days = request.getDays();
        this.status = request.getStatus();
        this.approvalRequestId = request.getApprovalRequestId();
    }

    public static LeaveManageResponse of(LeaveRequest request) {
        return new LeaveManageResponse(request);
    }

}
