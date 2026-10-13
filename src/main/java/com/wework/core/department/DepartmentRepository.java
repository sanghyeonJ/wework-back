package com.wework.core.department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // id로 1개 조회 (내 회사 + 상태) → ACTIVE로 조회하면 삭제된 부서는 "없음"
    Optional<Department> findByIdAndCompanyIdAndStatus(Long id, Long companyId, DepartmentStatus status);

    // 목록 조회 (내 회사 + 상태)
    List<Department> findAllByCompanyIdAndStatus(Long companyId, DepartmentStatus status);

    // 생성 시: 같은 회사에 같은 이름의 부서가 있는지 (ACTIVE끼리만)
    boolean existsByCompanyIdAndNameAndStatus(Long companyId, String name, DepartmentStatus status);

    // 이름 변경 시: 나 자신을 빼고 같은 이름이 있는지
    boolean existsByCompanyIdAndNameAndStatusAndIdNot(Long companyId, String name, DepartmentStatus status, Long id);

    // 회사에 최상위 부서가 이미 있는지 (ACTIVE끼리만)
    boolean existsByCompanyIdAndParentIsNullAndStatus(Long companyId, DepartmentStatus status);

    // 삭제 시: ACTIVE 하위 부서가 있는지
    boolean existsByParentIdAndStatus(Long parentId, DepartmentStatus status);

}