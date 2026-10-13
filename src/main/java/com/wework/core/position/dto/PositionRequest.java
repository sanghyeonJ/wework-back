package com.wework.core.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PositionRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    // 작을수록 높음 (10, 20, 30처럼 간격 두기 권장)
    @NotNull
    @Positive
    private Integer level;

}
