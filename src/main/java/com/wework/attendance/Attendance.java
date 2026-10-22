package com.wework.attendance;

import com.wework.core.company.Company;
import com.wework.core.member.Member;
import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_member_work_date",
                columnNames = {"member_id", "work_date"}
        ),
        indexes = @Index(
                name = "idx_attendance_company_work_date",
                columnList = "company_id, work_date"
        )
)
public class Attendance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "late", nullable = false)
    private boolean late;

    @Column(name = "early_leave", nullable = false)
    private boolean earlyLeave;

    private Attendance(Company company, Member member, LocalDate workDate, Instant checkInAt, boolean late) {
        this.company = company;
        this.member = member;
        this.workDate = workDate;
        this.checkInAt = checkInAt;
        this.late = late;
        this.earlyLeave = false;
    }

    public static Attendance create(Company company, Member member, LocalDate workDate, Instant checkInAt, boolean late) {
        return new Attendance(company, member, workDate, checkInAt, late);
    }

    // 퇴근 기록
    public void checkOut(Instant checkOutAt, boolean earlyLeave) {
        this.checkOutAt = checkOutAt;
        this.earlyLeave = earlyLeave;
    }

    // 상태만 알려주는 도우미
    public boolean isCheckedOut() {
        return checkOutAt != null;
    }

}
