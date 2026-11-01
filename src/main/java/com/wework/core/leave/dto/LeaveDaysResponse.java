package com.wework.core.leave.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class LeaveDaysResponse {

    private final BigDecimal days;

    private LeaveDaysResponse(BigDecimal days) {
        this.days = days;
    }

    public static LeaveDaysResponse of(BigDecimal days) {
        return new LeaveDaysResponse(days);
    }

}
