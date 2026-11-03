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
        name = "approval_step",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_approval_step_request_id_step",
                columnNames = {"request_id", "step"}
        )
)
public class ApprovalStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(nullable = false)
    private int step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private Member approver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private Member processedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStepStatus status;

    @Column(length = 500)
    private String comment;

    @Column(name = "processed_at")
    private Instant processedAt;

    private ApprovalStep(Company company, ApprovalRequest request, int step, Member approver) {
        this.company = company;
        this.request = request;
        this.step = step;
        this.approver = approver;
        this.status = (step == 1) ? ApprovalStepStatus.PENDING : ApprovalStepStatus.WAITING;
    }

    public static ApprovalStep create(Company company, ApprovalRequest request, int step, Member approver) {
        return new ApprovalStep(company, request, step, approver);
    }

}
