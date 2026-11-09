package com.wework.core.leave.dto;

import com.wework.core.leave.entity.LeaveType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
public class LeaveApplyRequest {

    @NotNull
    private LeaveType leaveType;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @Size(max = 500)
    private String reason;

    @NotEmpty
    @Size(max = 5)
    private List<@NotNull Long> approverIds;

}
