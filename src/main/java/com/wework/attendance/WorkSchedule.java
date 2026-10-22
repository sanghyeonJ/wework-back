package com.wework.attendance;

import com.wework.core.company.Company;
import com.wework.core.member.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "work_schedule",
        indexes = @Index(name = "idx_work_schedule_company_scope", columnList = "company_id, scope_type")
)
public class WorkSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private WorkScheduleScope scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // 지각 유예 분
    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;

    private WorkSchedule(Company company, WorkScheduleScope scopeType, Member member, LocalTime startTime, LocalTime endTime, int graceMinutes) {
        this.company = company;
        this.scopeType = scopeType;
        this.member = member;
        this.startTime = startTime;
        this.endTime = endTime;
        this.graceMinutes = graceMinutes;
    }

    public static WorkSchedule createForCompany(Company company, LocalTime startTime, LocalTime endTime, int graceMinutes) {
        return new WorkSchedule(company, WorkScheduleScope.COMPANY, null, startTime, endTime, graceMinutes);
    }

    public static WorkSchedule createForMember(Company company, Member member, LocalTime startTime, LocalTime endTime, int graceMinutes) {
        return new WorkSchedule(company, WorkScheduleScope.MEMBER, member, startTime, endTime, graceMinutes);
    }

    // 시간 수정
    public void update(LocalTime startTime, LocalTime endTime, int graceMinutes) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.graceMinutes = graceMinutes;
    }

}
