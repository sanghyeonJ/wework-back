package com.wework.core.leave.dto;

import lombok.Getter;

@Getter
public class LeaveBalanceMemberResponse {

    private final Long memberId;
    private final String memberName;
    private final String departmentName;
    private final LeaveBalanceResponse balance;

    private LeaveBalanceMemberResponse(Long memberId, String memberName, String departmentName, LeaveBalanceResponse balance) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.departmentName = departmentName;
        this.balance = balance;
    }

    public static LeaveBalanceMemberResponse of(Long memberId, String memberName, String departmentName, LeaveBalanceResponse balance) {
        return new LeaveBalanceMemberResponse(memberId, memberName, departmentName, balance);
    }
}
