package com.wework.core.leave.entity;

import com.wework.core.company.entity.Company;
import com.wework.core.member.entity.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "leave_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_balance_member_year",
                columnNames = {"member_id", "year"}
        )
)
public class LeaveBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "granted_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal grantedDays;

    @Column(name = "used_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal usedDays;

    private LeaveBalance(Company company, Member member, int year, BigDecimal grantedDays) {
        this.company = company;
        this.member = member;
        this.year = year;
        this.grantedDays = grantedDays;
        this.usedDays = BigDecimal.ZERO;
    }

    public static LeaveBalance create(Company company, Member member, int year, BigDecimal grantedDays) {
        return new LeaveBalance(company, member, year, grantedDays);
    }

    public void changeGrantedDays(BigDecimal grantedDays) {
        this.grantedDays = grantedDays;
    }

    public void useDays(BigDecimal days) {
        this.usedDays = usedDays.add(days);
    }

    public void restoreDays(BigDecimal days) {
        this.usedDays = usedDays.subtract(days);
    }

}
