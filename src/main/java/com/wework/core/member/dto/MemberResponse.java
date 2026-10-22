package com.wework.core.member.dto;

import com.wework.core.department.Department;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRole;
import com.wework.core.member.MemberStatus;
import com.wework.core.position.JobGrade;
import com.wework.core.position.JobTitle;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class MemberResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final MemberRole role;
    private final MemberStatus status;
    private final Long departmentId;
    private final String departmentName;
    private final Long jobGradeId;
    private final String jobGradeName;
    private final Long jobTitleId;
    private final String jobTitleName;
    private final LocalDate hireDate;
    private final LocalDate resignDate;
    private final boolean leader;  // ACTIVE 부서의 부서장인지 (member가 아닌 department.leader_id 기준)

    private MemberResponse(Member member, boolean leader) {
        Department department = member.getDepartment();
        JobGrade jobGrade = member.getJobGrade();
        JobTitle jobTitle = member.getJobTitle();

        this.id = member.getId();
        this.email = member.getEmail();
        this.name = member.getName();
        this.role = member.getRole();
        this.status = member.getStatus();
        this.departmentId = department != null ? department.getId() : null;
        this.departmentName = department != null ? department.getName(): null;
        this.jobGradeId = jobGrade != null ? jobGrade.getId() : null;
        this.jobGradeName = jobGrade != null ? jobGrade.getName() : null;
        this.jobTitleId = jobTitle != null ? jobTitle.getId() : null;
        this.jobTitleName = jobTitle != null ? jobTitle.getName() : null;
        this.hireDate = member.getHireDate();
        this.resignDate = member.getResignDate();
        this.leader = leader;
    }

    public static MemberResponse of(Member member, boolean leader) {
        return new MemberResponse(member, leader);
    }

}
