package com.wework.core.approval.dto;

import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.approval.entity.ApprovalStepStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ApprovalStepResponse {

    private final int step;
    private final String approverName;
    private final String processedByName;
    private final ApprovalStepStatus status;
    private final String comment;
    private final Instant processedAt;

    private ApprovalStepResponse(int step, String approverName, String processedByName, ApprovalStepStatus status, String comment, Instant processedAt) {
        this.step = step;
        this.approverName = approverName;
        this.processedByName = processedByName;
        this.status = status;
        this.comment = comment;
        this.processedAt = processedAt;
    }

    public static ApprovalStepResponse of(ApprovalStep step) {
        return new ApprovalStepResponse(
                step.getStep(),
                step.getApprover().getName(),
                step.getProcessedBy() == null ? null : step.getProcessedBy().getName(),
                step.getStatus(),
                step.getComment(),
                step.getProcessedAt()
        );
    }

}
