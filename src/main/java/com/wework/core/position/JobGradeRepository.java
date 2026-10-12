package com.wework.core.position;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobGradeRepository extends JpaRepository<JobGrade, Long> {

    // id로 1개 조회 (내 회사 것만)
    Optional<JobGrade> findByIdAndCompanyId(Long id, Long companyId);

    // 목록 조회 (내 회사, level 작은 순 = 높은 직급부터)
    List<JobGrade> findAllByCompanyIdOrderByLevelAsc(Long companyId);

    // 같은 회사에 같은 이름의 직급이 있는지
    boolean existsByCompanyIdAndName(Long companyId, String name);

}
