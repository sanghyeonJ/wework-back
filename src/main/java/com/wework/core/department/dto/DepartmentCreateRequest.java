package com.wework.core.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    // null이면 최상위 부서로 생성
    private Long parentId;

}
