package com.wework.core.leave.entity;

public enum LeaveType {
    ANNUAL,
    HALF_AM,
    HALF_PM,
    SICK;

    public boolean isHalfDay() {
        return this == HALF_AM || this == HALF_PM;
    }

    public boolean isDeductible() {
        return this == ANNUAL || isHalfDay();
    }
}
