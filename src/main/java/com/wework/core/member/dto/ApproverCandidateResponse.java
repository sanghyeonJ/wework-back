package com.wework.core.member.dto;

import com.wework.core.department.entity.Department;
import com.wework.core.member.entity.Member;
import com.wework.core.position.entity.JobGrade;
import com.wework.core.position.entity.JobTitle;
import lombok.Getter;

@Getter
public class ApproverCandidateResponse {

    private final Long id;
    private final String name;
    private final String departmentName;
    private final String jobGradeName;
    private final String jobTitleName;

    private ApproverCandidateResponse(Member member) {
        Department department = member.getDepartment();
        JobGrade jobGrade = member.getJobGrade();
        JobTitle jobTitle = member.getJobTitle();

        this.id = member.getId();
        this.name = member.getName();
        this.departmentName = department != null ? department.getName() : null;
        this.jobGradeName = jobGrade != null ? jobGrade.getName() : null;
        this.jobTitleName = jobTitle != null ? jobTitle.getName() : null;
    }

    public static ApproverCandidateResponse of(Member member) {
        return new ApproverCandidateResponse(member);
    }

}
