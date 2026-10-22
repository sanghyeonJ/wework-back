package com.wework.attendance;

import com.wework.attendance.dto.AttendanceResponse;
import com.wework.attendance.dto.TodayAttendanceResponse;
import com.wework.core.member.Member;
import com.wework.core.member.MemberErrorCode;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberStatus;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final WorkScheduleService workScheduleService;

    // ===== 출근 =====
    @Transactional
    public AttendanceResponse checkIn(Long companyId, Long memberId) {
        Member member = findActiveMember(memberId, companyId);
        ZoneId zone = zoneOf(member);
        Instant now = Instant.now();
        LocalDate workDate = now.atZone(zone).toLocalDate();

        Attendance existing = attendanceRepository
                .findByCompanyIdAndMemberIdAndWorkDate(companyId, memberId, workDate)
                .orElse(null);
        if (existing != null){
            return AttendanceResponse.of(existing);
        }

        EffectiveWorkSchedule schedule = workScheduleService.findEffective(companyId, memberId);
        boolean late = isLate(now.atZone(zone).toLocalTime(), schedule);

        Attendance attendance = Attendance.create(member.getCompany(), member, workDate, now, late);

        try{
            attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AttendanceErrorCode.ALREADY_CHECKED_IN);
        }
        return AttendanceResponse.of(attendance);
    }

    // ===== 퇴근 =====
    @Transactional
    public AttendanceResponse checkOut(Long companyId, Long memberId) {
        Member member = findActiveMember(memberId, companyId);
        ZoneId zone = zoneOf(member);
        Instant now = Instant.now();
        LocalDate workDate = now.atZone(zone).toLocalDate();

        // 출근 여부
        Attendance attendance = attendanceRepository
                .findByCompanyIdAndMemberIdAndWorkDate(companyId, memberId, workDate)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.NOT_CHECKED_IN));

        // 조퇴 확인
        EffectiveWorkSchedule schedule = workScheduleService.findEffective(companyId, memberId);
        boolean earlyLeave = isEarlyLeave(now.atZone(zone).toLocalTime(), schedule);

        attendance.checkOut(now, earlyLeave);
        return AttendanceResponse.of(attendance);
    }

    // ===== 조회 =====
    // 오늘 내 기록 (출근 전이면 attendance가 null)
    public TodayAttendanceResponse getToday(Long companyId, Long memberId) {
        Member member = findMember(memberId, companyId);
        LocalDate workDate = LocalDate.now(zoneOf(member));

        AttendanceResponse attendance = attendanceRepository
                .findByCompanyIdAndMemberIdAndWorkDate(companyId, memberId, workDate)
                .map(AttendanceResponse::of)
                .orElse(null);
        return TodayAttendanceResponse.of(workDate, attendance);
    }

    // 내 월별 기록
    public List<AttendanceResponse> getMonthly(Long companyId, Long memberId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        YearMonth yearMonth = YearMonth.of(year, month);

        return attendanceRepository
                .findAllByCompanyIdAndMemberIdAndWorkDateBetweenOrderByWorkDateAsc(
                        companyId, memberId, yearMonth.atDay(1), yearMonth.atEndOfMonth()
                )
                .stream()
                .map(AttendanceResponse::of)
                .toList();
    }


    // 지각
    static boolean isLate(LocalTime checkInTime, EffectiveWorkSchedule schedule) {
        int limitMinute = minuteOfDay(schedule.getStartTime()) + schedule.getGraceMinutes();
        return minuteOfDay(checkInTime) > limitMinute;
    }

    // 조퇴
    static boolean isEarlyLeave(LocalTime checkOutTime, EffectiveWorkSchedule schedule) {
        return minuteOfDay(checkOutTime) < minuteOfDay(schedule.getEndTime());
    }

    // 하루 중 몇 분째인지
    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }


    // ===== 내부 도우미 =====
    private Member findMember(Long memberId, Long companyId) {
        return memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    // 기록을 남기는 작업은 퇴사자 불가
    private Member findActiveMember(Long memberId, Long companyId) {
        Member member = findMember(memberId, companyId);
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(MemberErrorCode.MEMBER_RESIGNED);
        }
        return member;
    }

    private ZoneId zoneOf(Member member) {
        return ZoneId.of(member.getCompany().getTimeZone());
    }

}
