package com.wework.core.leave.dto;

import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class LeaveSummaryResponse {

    private final Long id;
    private final LeaveType leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal days;
    private final LeaveRequestStatus status;
    private final Long approvalRequestId;

    private LeaveSummaryResponse(LeaveRequest request) {
        this.id = request.getId();
        this.leaveType = request.getLeaveType();
        this.startDate = request.getStartDate();
        this.endDate = request.getEndDate();
        this.days = request.getDays();
        this.status = request.getStatus();
        this.approvalRequestId = request.getApprovalRequestId();
    }

    public static LeaveSummaryResponse of(LeaveRequest request) {
        return new LeaveSummaryResponse(request);
    }

}
