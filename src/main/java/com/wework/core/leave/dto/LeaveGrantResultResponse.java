package com.wework.core.leave.dto;

import lombok.Getter;

@Getter
public class LeaveGrantResultResponse {

    private final int year;
    private final int grantedCount;

    private LeaveGrantResultResponse(int year, int grantedCount) {
        this.year = year;
        this.grantedCount = grantedCount;
    }

    public static LeaveGrantResultResponse of(int year, int grantedCount) {
        return new LeaveGrantResultResponse(year, grantedCount);
    }

}
