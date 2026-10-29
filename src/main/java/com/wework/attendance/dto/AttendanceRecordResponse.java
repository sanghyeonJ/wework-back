package com.wework.attendance.dto;

import com.wework.attendance.entity.Attendance;
import com.wework.core.member.entity.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 관리자 근태 조회의 한 줄: 사원 + 그날 기록 (기록이 없으면 attendance가 null)
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AttendanceRecordResponse {

    private final Long memberId;
    private final String memberName;
    private final String departmentName;  // 부서가 없으면 null
    private final AttendanceResponse attendance;  // 기록이 없으면 null

    public static AttendanceRecordResponse of(Member member, Attendance attendance){
        return new AttendanceRecordResponse(
                member.getId(),
                member.getName(),
                member.getDepartment() != null ? member.getDepartment().getName() : null,
                attendance != null ? AttendanceResponse.of(attendance) : null
        );
    }

}
