package com.wework.core.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompanyHolidayRepository extends JpaRepository<CompanyHoliday, Long> {

    // 수정·삭제용: 다른 회사 id면 비어 있음 → 서비스가 HOLIDAY_NOT_FOUND(404)
    Optional<CompanyHoliday> findByIdAndCompanyId(Long id, Long companyId);

    // 등록 시 같은 날짜 중복 검사
    boolean existsByCompanyIdAndHolidayDate(Long companyId, LocalDate holidayDate);

    // 수정 시 중복 검사 (자기 자신 제외)
    boolean existsByCompanyIdAndHolidayDateAndIdNot(Long companyId, LocalDate holidayDate, Long id);

    // 기간 안의 휴일 + 매년 반복 휴일 전부. 반복 휴일은 등록한 연도와 상관없이 가져옴
    @Query("""
        select h
        from CompanyHoliday h
        where h.company.id = :companyId
            and (h.recurring = true or h.holidayDate between :from and :to)
        order by h.holidayDate asc, h.id asc
    """)
    List<CompanyHoliday> findAllInRange(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

}
