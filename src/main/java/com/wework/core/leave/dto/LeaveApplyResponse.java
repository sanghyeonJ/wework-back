package com.wework.core.leave.dto;

import lombok.Getter;

@Getter
public class LeaveApplyResponse {

    private final Long id;

    private LeaveApplyResponse(Long id) {
        this.id = id;
    }

    public static LeaveApplyResponse of(Long id) {
        return new LeaveApplyResponse(id);
    }

}
