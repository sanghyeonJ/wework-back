package com.wework.core.leave.entity;

import com.wework.core.company.entity.Company;
import com.wework.core.member.entity.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(precision = 4, scale = 1, nullable = false)
    private BigDecimal days;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestStatus status;
    private Long approvalRequestId;

    private LeaveRequest(Company company, Member member, LeaveType leaveType, LocalDate startDate, LocalDate endDate, BigDecimal days, String reason) {
        this.company = company;
        this.member = member;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
        this.status = LeaveRequestStatus.PENDING;
    }

    public static LeaveRequest create(Company company, Member member, LeaveType leaveType, LocalDate startDate, LocalDate endDate, BigDecimal days, String reason) {
        return new LeaveRequest(company, member, leaveType, startDate, endDate, days, reason);
    }

    public void linkApproval(Long approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

}
