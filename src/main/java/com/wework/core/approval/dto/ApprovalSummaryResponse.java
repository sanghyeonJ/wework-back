package com.wework.core.approval.dto;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ApprovalSummaryResponse {

    private final Long id;
    private final String targetType;
    private final Long targetId;
    private final String title;
    private final String requesterName;
    private final ApprovalRequestStatus status;
    private final Instant requestedAt;

    private ApprovalSummaryResponse(Long id, String targetType, Long targetId, String title, String requesterName, ApprovalRequestStatus status, Instant requestedAt) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.requesterName = requesterName;
        this.status = status;
        this.requestedAt = requestedAt;
    }

    public static ApprovalSummaryResponse of(ApprovalRequest request) {
        return new ApprovalSummaryResponse(
                request.getId(),
                request.getTargetType(),
                request.getTargetId(),
                request.getTitle(),
                request.getRequester().getName(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }

}
