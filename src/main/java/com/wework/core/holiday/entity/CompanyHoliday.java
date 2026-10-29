package com.wework.core.holiday.entity;

import com.wework.core.company.entity.Company;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "company_holiday",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_holiday_company_date",
                columnNames = {"company_id", "holiday_date"}
        )
)
public class CompanyHoliday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 100)
    private String name;

    // true면 해마다 반복 (월·일만 비교)
    @Column(nullable = false)
    private boolean recurring;

    private CompanyHoliday(Company company, LocalDate holidayDate, String name, boolean recurring) {
        this.company = company;
        this.holidayDate = holidayDate;
        this.name = name;
        this.recurring = recurring;
    }

    public static CompanyHoliday create(Company company, LocalDate holidayDate, String name, boolean recurring) {
        return new CompanyHoliday(company, holidayDate, name, recurring);
    }

}
