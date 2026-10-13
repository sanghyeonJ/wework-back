package com.wework.core.department.dto;

import com.wework.core.department.Department;
import com.wework.core.member.Member;
import lombok.Getter;

@Getter
public class DepartmentResponse {

    private final Long id;
    private final String name;
    private final Long parentId;
    private final Long leaderId;
    private final String leaderName;

    private DepartmentResponse(Long id, String name, Long parentId, Long leaderId, String leaderName) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.leaderId = leaderId;
        this.leaderName = leaderName;
    }

    public  static DepartmentResponse of(Department department) {
        Department parent = department.getParent();
        Member leader = department.getLeader();

        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                parent != null ? parent.getId() : null,
                leader != null ? leader.getId() : null,
                leader != null ? leader.getName() : null
        );
    }

}
