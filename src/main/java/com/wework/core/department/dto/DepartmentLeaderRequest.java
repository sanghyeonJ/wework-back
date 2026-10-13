package com.wework.core.department.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentLeaderRequest {

    // null이면 부서장 해제
    private Long leaderId;

}
