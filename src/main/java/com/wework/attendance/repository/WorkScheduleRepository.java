package com.wework.attendance.repository;

import com.wework.attendance.entity.WorkSchedule;
import com.wework.attendance.entity.WorkScheduleScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    // 회사 일괄 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeType(Long companyId, WorkScheduleScope scopeType);

    // 개인 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeTypeAndMemberId(Long companyId, WorkScheduleScope scopeType, Long memberId);

    // 사원 여러 명의 개인 설정을 한 번에 조회 (일괄 저장 시 있으면 수정, 없으면 생성하려고)
    // memberIds는 비어 있으면 안 됨
    List<WorkSchedule> findAllByCompanyIdAndScopeTypeAndMemberIdIn(
            Long companyId, WorkScheduleScope scopeType, Collection<Long> memberIds
    );

    // 회사의 설정 전부
    // 개인 설정의 사원 이름을 항목마다 조회하지 않도록 fetch join (N+1 방지)
    @Query("select ws from WorkSchedule ws left join fetch ws.member " +
            "where ws.company.id = :companyId order by ws.scopeType asc, ws.id asc")
    List<WorkSchedule> findAllWithMemberByCompanyId(@Param("companyId") Long companyId);

}
