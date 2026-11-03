package com.wework.core.approval.entity;

import com.wework.core.company.entity.Company;
import com.wework.core.member.entity.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "approval_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_approval_request_target_type_target_id",
                columnNames = {"target_type", "target_id"}
        ),
        indexes = @Index(
                name = "idx_approval_request_company_id_status",
                columnList = "company_id, status"
        )
)
public class ApprovalRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private Member requester;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    private ApprovalRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canceled_by")
    private Member canceledBy;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    private Long version;

    private ApprovalRequest(Company company, Member requester, String targetType, Long targetId, String title) {
        this.company = company;
        this.requester = requester;
        this.targetType = targetType;
        this.targetId = targetId;
        this.title = title;
        this.status = ApprovalRequestStatus.PENDING;
    }

    public static ApprovalRequest create(Company company, Member requester, String targetType, Long targetId, String title) {
        return new ApprovalRequest(company, requester, targetType, targetId, title);
    }

}
