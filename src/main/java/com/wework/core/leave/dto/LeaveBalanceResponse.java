package com.wework.core.leave.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class LeaveBalanceResponse {

    private final int year;
    private final BigDecimal grantedDays;
    private final BigDecimal usedDays;
    private final BigDecimal remainingDays;
    private final BigDecimal pendingDays;

    private LeaveBalanceResponse(int year, BigDecimal grantedDays, BigDecimal usedDays, BigDecimal remainingDays, BigDecimal pendingDays) {
        this.year = year;
        this.grantedDays = grantedDays;
        this.usedDays = usedDays;
        this.remainingDays = remainingDays;
        this.pendingDays = pendingDays;
    }

    public static LeaveBalanceResponse of(int year, BigDecimal grantedDays, BigDecimal usedDays, BigDecimal remainingDays, BigDecimal pendingDays) {
        return new LeaveBalanceResponse(year, grantedDays, usedDays, remainingDays, pendingDays);
    }

}
