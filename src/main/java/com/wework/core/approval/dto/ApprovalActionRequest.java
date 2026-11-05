package com.wework.core.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalActionRequest {

    @Size(max = 500)
    private String comment;

}
