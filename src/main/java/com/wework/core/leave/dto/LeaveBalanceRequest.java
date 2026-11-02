package com.wework.core.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class LeaveBalanceRequest {

    @NotNull
    private BigDecimal grantedDays;

}
