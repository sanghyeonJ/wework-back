package com.wework.core.member.dto;

import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.core.position.entity.JobGrade;
import com.wework.core.position.entity.JobTitle;
import lombok.Getter;

@Getter
public class MeResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final MemberRole role;
    private final Long companyId;        // 최고관리자는 null
    private final String companyName;    // 최고관리자는 null
    private final Long departmentId;
    private final String departmentName;
    private final String jobGradeName;
    private final String jobTitleName;

    private MeResponse(Member member) {
        Company company = member.getCompany();
        Department department = member.getDepartment();
        JobGrade jobGrade = member.getJobGrade();
        JobTitle jobTitle = member.getJobTitle();

        this.id = member.getId();
        this.email = member.getEmail();
        this.name = member.getName();
        this.role = member.getRole();
        this.companyId = company != null ? company.getId() : null;
        this.companyName = company != null ? company.getName() : null;
        this.departmentId = department != null ? department.getId() : null;
        this.departmentName = department != null ? department.getName() : null;
        this.jobGradeName = jobGrade != null ? jobGrade.getName() : null;
        this.jobTitleName = jobTitle != null ? jobTitle.getName() : null;
    }

    public static MeResponse of(Member member) {
        return new MeResponse(member);
    }

}
