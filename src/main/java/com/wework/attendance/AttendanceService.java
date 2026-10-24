package com.wework.attendance;

import com.wework.attendance.dto.AttendanceRecordResponse;
import com.wework.attendance.dto.AttendanceResponse;
import com.wework.attendance.dto.TodayAttendanceResponse;
import com.wework.core.department.DepartmentErrorCode;
import com.wework.core.department.DepartmentRepository;
import com.wework.core.department.DepartmentService;
import com.wework.core.department.DepartmentStatus;
import com.wework.core.member.Member;
import com.wework.core.member.MemberErrorCode;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberStatus;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final MemberRepository memberRepository;
    private final WorkScheduleService workScheduleService;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;

    // 부서 조건을 안 쓸 때 IN 자리에 넣는 값 (IN에는 빈 목록을 넣을 수 없음)
    private static final Long UNUSED_DEPARTMENT_ID = 0L;
    // 관리자 조회 한 페이지 최대 인원 (클라이언트가 size를 크게 보내도 이 값으로 제한)
    private static final int MAX_PAGE_SIZE = 100;

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

    // 관리자 조회: 그날 재직 중이던 사원(한 페이지) + 기록 (없으면 null)
    public Page<AttendanceRecordResponse> getRecords(Long companyId, LocalDate date, Long departmentId, boolean includeSubDepartments, Pageable pageable) {
        boolean allDepartments = (departmentId == null);
        List<Long> departmentIds = List.of(UNUSED_DEPARTMENT_ID);

        if(!allDepartments) {
            // [서비스 판단] 내 회사의 ACTIVE 부서인가? (아니면 404) → 하위 부서 계산보다 먼저
            departmentRepository.findByIdAndCompanyIdAndStatus(departmentId, companyId, DepartmentStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(DepartmentErrorCode.DEPARTMENT_NOT_FOUND));

            departmentIds = includeSubDepartments
                    ? departmentService.findSubtreeIds(companyId, departmentId)
                    : List.of(departmentId);
        }

        // 정렬은 쿼리에 고정(이름순 → id순) → sort는 버리고 page, size만 사용, size는 상한 적용
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)
        );

        // 사원: 그날 재직 중이던 사원의 한 페이지
        Page<Member> members = memberRepository.findEmployedOn(companyId, date, allDepartments, departmentIds, safePageable);

        // 기록: 그 페이지 사원들의 것만 한 번에 조회 → memberId로 찾기 쉽게 Map으로
        // (IN에 빈 목록 금지 → 이 페이지에 사원이 없으면 조회하지 않음)
        List<Long> memberIds = members.getContent().stream().map(Member::getId).toList();
        Map<Long, Attendance> attendanceByMemberId = memberIds.isEmpty()
                ? Map.of()
                : attendanceRepository.findAllByCompanyIdAndWorkDateAndMemberIdIn(companyId, date, memberIds).stream()
                    .collect(Collectors.toMap(a -> a.getMember().getId(), a -> a));

        // Page.map: 내용만 바꾸고 전체 개수·페이지 정보는 그대로 유지
        return members.map(member -> AttendanceRecordResponse.of(member, attendanceByMemberId.get(member.getId())));
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
