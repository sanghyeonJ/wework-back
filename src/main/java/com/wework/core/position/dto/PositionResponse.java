package com.wework.core.position.dto;

import com.wework.core.position.JobGrade;
import com.wework.core.position.JobTitle;
import lombok.Getter;

@Getter
public class PositionResponse {

    private final Long id;
    private final String name;
    private final Integer level;

    private PositionResponse(Long id, String name, Integer level) {
        this.id = id;
        this.name = name;
        this.level = level;
    }

    public static PositionResponse of(JobGrade jobGrade) {
        return new PositionResponse(jobGrade.getId(), jobGrade.getName(), jobGrade.getLevel());
    }

    public static PositionResponse of(JobTitle jobTitle) {
        return new PositionResponse(jobTitle.getId(), jobTitle.getName(), jobTitle.getLevel());
    }

}
