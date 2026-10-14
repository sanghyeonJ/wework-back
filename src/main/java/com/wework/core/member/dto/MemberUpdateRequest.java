package com.wework.core.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MemberUpdateRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    private Long departmentId;
    private Long jobGradeId;
    private Long jobTitleId;
    private LocalDate hireDate;

}
