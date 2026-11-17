package com.wework.core.leave.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class LeaveGrantRuleRequest {

    @NotNull
    @Valid
    private List<Item> rules;

    @Getter
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Integer serviceYear;

        @NotNull
        private BigDecimal grantedDays;
    }

}
