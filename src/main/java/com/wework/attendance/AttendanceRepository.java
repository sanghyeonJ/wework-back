package com.wework.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // 특정 날짜의 내 기록 (출근·퇴근·오늘 조회에서 사용)
    Optional<Attendance> findByCompanyIdAndMemberIdAndWorkDate(Long companyId, Long memberId, LocalDate workDate);

    // 기간 내 내 기록 (월별 조회용, from·to 날짜 포함)
    List<Attendance> findAllByCompanyIdAndMemberIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long companyId, Long memberId, LocalDate from, LocalDate to
    );

}
