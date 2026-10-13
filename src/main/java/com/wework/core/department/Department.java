package com.wework.core.department;

import com.wework.core.company.Company;
import com.wework.core.member.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "department")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private Member leader;

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

    // 최상위 부서인지 (상위 부서 없음)
    public boolean isRoot() {
        return parent == null;
    }

    // 이름 변경
    public void changeName(String name) {
        this.name = name;
    }

    // 상위 부서 변경 (같은 회사·순환 여부는 Service에서 확인)
    public void changeParent(Department parent) {
        this.parent = parent;
    }

    // 부서장 지정 / 해제 (null이면 해제)
    public void changeLeader(Member leader) {
        this.leader = leader;
    }

    // 삭제 = INACTIVE 처리
    public void deactivate() {
        this.status = DepartmentStatus.INACTIVE;
    }

}