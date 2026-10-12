package com.wework.core.member;

import com.wework.core.company.Company;
import com.wework.core.department.Department;
import com.wework.core.position.JobGrade;
import com.wework.core.position.JobTitle;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Locale;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id")
    private JobGrade jobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Builder
    private Member(Company company, Department department, JobGrade jobGrade, JobTitle jobTitle,
                   String email, String name, MemberRole role, LocalDate hireDate) {
        this.company = company;
        this.department = department;
        this.jobGrade = jobGrade;
        this.jobTitle = jobTitle;
        this.email = normalizeEmail(email);
        this.name = name;
        this.role = (role != null) ? role : MemberRole.EMPLOYEE;
        this.status = MemberStatus.INVITED;
        this.hireDate = hireDate;
    }

    public static String normalizeEmail(String email) {
        return (email != null) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

}
