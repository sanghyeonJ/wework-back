package com.wework.core.position;

import com.wework.core.company.Company;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "job_title",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobTitle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer level;

    private JobTitle(Company company, String name, Integer level) {
        this.company = company;
        this.name = name;
        this.level = level;
    }

    public static JobTitle create(Company company, String name, Integer level) {
        return new JobTitle(company, name, level);
    }

}
