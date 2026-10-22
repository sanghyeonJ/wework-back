package com.wework.core.member.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MemberResignRequest {

    // 미래 불가, 입사일 이전 불가 (서비스에서 회사 시간대 기준으로 검사)
    @NotNull
    private LocalDate resignDate;

}
