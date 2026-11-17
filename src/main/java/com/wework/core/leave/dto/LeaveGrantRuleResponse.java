package com.wework.core.leave.dto;

import com.wework.core.leave.entity.LeaveGrantRule;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class LeaveGrantRuleResponse {

    private final int serviceYear;
    private final BigDecimal grantedDays;

    private LeaveGrantRuleResponse(int serviceYear, BigDecimal grantedDays) {
        this.serviceYear = serviceYear;
        this.grantedDays = grantedDays;
    }

    public static LeaveGrantRuleResponse of(LeaveGrantRule rule) {
        return new LeaveGrantRuleResponse(rule.getServiceYear(), rule.getGrantedDays());
    }

}
