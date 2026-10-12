package com.wework.core.position;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

    // id로 1개 조회 (내 회사 것만)
    Optional<JobTitle> findByIdAndCompanyId(Long id, Long companyId);

    // 목록 조회 (내 회사, level 작은 순 = 높은 직책부터)
    List<JobTitle> findAllByCompanyIdOrderByLevelAsc(Long companyId);

    // 같은 회사에 같은 이름의 직책이 있는지
    boolean existsByCompanyIdAndName(Long companyId, String name);

}
