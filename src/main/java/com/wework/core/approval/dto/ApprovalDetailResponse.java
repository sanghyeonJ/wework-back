package com.wework.core.approval.dto;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStep;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ApprovalDetailResponse {

    private final Long id;
    private final String targetType;
    private final Long targetId;
    private final String title;
    private final String requesterName;
    private final ApprovalRequestStatus status;
    private final Instant requestedAt;
    private final String canceledByName;
    private final Instant canceledAt;
    private final String cancelReason;
    private final List<ApprovalStepResponse> steps;

    private ApprovalDetailResponse(Long id, String targetType, Long targetId, String title, String requesterName, ApprovalRequestStatus status, Instant requestedAt, String canceledByName, Instant canceledAt, String cancelReason, List<ApprovalStepResponse> steps) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.requesterName = requesterName;
        this.status = status;
        this.requestedAt = requestedAt;
        this.canceledByName = canceledByName;
        this.canceledAt = canceledAt;
        this.cancelReason = cancelReason;
        this.steps = steps;
    }

    public static ApprovalDetailResponse of(ApprovalRequest request, List<ApprovalStep> steps) {
        List<ApprovalStepResponse> stepResponses = new ArrayList<>();
        for (ApprovalStep step : steps) {
            ApprovalStepResponse stepResponse = ApprovalStepResponse.of(step);
            stepResponses.add(stepResponse);
        }

        return new ApprovalDetailResponse(
                request.getId(),
                request.getTargetType(),
                request.getTargetId(),
                request.getTitle(),
                request.getRequester().getName(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getCanceledBy() == null ? null : request.getCanceledBy().getName(),
                request.getCanceledAt(),
                request.getCancelReason(),
                stepResponses
        );
    }

}
