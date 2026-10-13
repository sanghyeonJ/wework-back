package com.wework.core.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentUpdateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    // 최상위 부서는 null, 나머지는 옮길 상위 부서 id (안 옮기면 현재 값 그대로 보냄)
    private Long parentId;

}
