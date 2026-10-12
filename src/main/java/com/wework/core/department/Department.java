package com.wework.core.department;

import com.wework.core.company.Company;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "department",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status;

    private Department(Company company, Department parent, String name) {
        this.company = company;
        this.parent = parent;
        this.name = name;
        this.status = DepartmentStatus.ACTIVE;
    }

    public static Department create(Company company, Department parent, String name) {
        return new Department(company, parent, name);
    }

}
