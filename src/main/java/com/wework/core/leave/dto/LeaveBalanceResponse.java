package com.wework.core.leave.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class LeaveBalanceResponse {

    private final int year;
    private final BigDecimal grantedDays;
    private final BigDecimal usedDays;
    private final BigDecimal remainingDays;

    private LeaveBalanceResponse(int year, BigDecimal grantedDays, BigDecimal usedDays, BigDecimal remainingDays) {
        this.year = year;
        this.grantedDays = grantedDays;
        this.usedDays = usedDays;
        this.remainingDays = remainingDays;
    }

    public static LeaveBalanceResponse of(int year, BigDecimal grantedDays, BigDecimal usedDays, BigDecimal remainingDays) {
        return new LeaveBalanceResponse(year, grantedDays, usedDays, remainingDays);
    }

}
