package com.wework.attendance.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 여러 사원에게 같은 개인 근무시간을 한 번에 저장하는 요청
// 시각·유예 분 필드와 검사 규칙은 WorkScheduleRequest를 그대로 물려받는다
@Getter
@NoArgsConstructor
public class WorkScheduleBulkRequest extends WorkScheduleRequest {

    @NotEmpty
    @Size(max = 100)
    private List<@NotNull Long> memberIds;

}
