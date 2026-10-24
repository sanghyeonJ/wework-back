package com.wework.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // 특정 날짜의 내 기록 (출근·퇴근·오늘 조회에서 사용)
    Optional<Attendance> findByCompanyIdAndMemberIdAndWorkDate(Long companyId, Long memberId, LocalDate workDate);

    // 기간 내 내 기록 (월별 조회용, from·to 날짜 포함)
    List<Attendance> findAllByCompanyIdAndMemberIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long companyId, Long memberId, LocalDate from, LocalDate to
    );

    // 특정 날짜의 회사 전체 기록 (관리자 조회용)
    List<Attendance> findAllByCompanyIdAndWorkDate(Long companyId, LocalDate workDate);

    // 특정 날짜의 기록 중, 주어진 사원들의 것만 (관리자 조회 페이징용)
    // memberIds는 비어 있으면 안 됨 → 서비스에서 빈 목록이면 호출하지 않는다
    List<Attendance> findAllByCompanyIdAndWorkDateAndMemberIdIn(
            Long companyId, LocalDate workDate, Collection<Long> memberIds
    );

}
