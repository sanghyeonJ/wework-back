package com.wework.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    // 회사 일괄 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeType(Long companyId, WorkScheduleScope scopeType);

    // 개인 설정
    Optional<WorkSchedule> findByCompanyIdAndScopeTypeAndMemberId(Long companyId, WorkScheduleScope scopeType, Long memberId);

    // 회사의 설정 전부
    List<WorkSchedule> findAllByCompanyId(Long companyId);

}
