package com.wework.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    // 회사 일괄 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeType(Long companyId, WorkScheduleScope scopeType);

    // 개인 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeTypeAndMemberId(Long companyId, WorkScheduleScope scopeType, Long memberId);

    // 회사의 설정 전부
    // 개인 설정의 사원 이름을 항목마다 조회하지 않도록 fetch join (N+1 방지)
    @Query("select ws from WorkSchedule ws left join fetch ws.member " +
            "where ws.company.id = :companyId order by ws.scopeType asc, ws.id asc")
    List<WorkSchedule> findAllWithMemberByCompanyId(@Param("companyId") Long companyId);

}
