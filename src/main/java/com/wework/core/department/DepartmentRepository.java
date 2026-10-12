package com.wework.core.department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // id로 1개 조회 (내 회사 것만)
    Optional<Department> findByIdAndCompanyId(Long id, Long companyId);

    // 목록 조회 (내 회사 + 상태)
    List<Department> findAllByCompanyIdAndStatus(Long companyId, DepartmentStatus status);

    // 같은 회사에 같은 이름의 부서가 있는지
    boolean existsByCompanyIdAndName(Long companyId, String name);

    // 회사에 최상위 부서(상위 부서 없음)가 이미 있는지
    boolean existsByCompanyIdAndParentIsNull(Long companyId);

}
