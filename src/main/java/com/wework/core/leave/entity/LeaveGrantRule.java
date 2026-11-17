package com.wework.core.leave.entity;

import com.wework.core.company.entity.Company;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "leave_grant_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_grant_rule_company_years",
                columnNames = {"company_id", "service_year"}
        )
)
public class LeaveGrantRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "service_year", nullable = false)
    private int serviceYear;

    @Column(name = "granted_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal grantedDays;

    private LeaveGrantRule(Company company, int serviceYear, BigDecimal grantedDays) {
        this.company = company;
        this.serviceYear = serviceYear;
        this.grantedDays = grantedDays;
    }

    public static LeaveGrantRule create(Company company, int serviceYear, BigDecimal grantedDays) {
        return new LeaveGrantRule(company, serviceYear, grantedDays);
    }

}
