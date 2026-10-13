package com.wework.core.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // ===== 로그인 · 계정 발급 (전체 시스템 기준) =====

    // 이메일로 회원 찾기 (로그인)
    Optional<Member> findByEmail(String email);

    // 이메일 중복 확인 (계정 발급)
    boolean existsByEmail(String email);

    // ===== 회사 안에서 조회 =====

    // id로 1개 조회 (내 회사 것만)
    Optional<Member> findByIdAndCompanyId(Long id, Long CompanyId);

    // 회사의 특정 권한 + 상태 회원 수 (기업관리자 최소 1명 유지)
    long countByCompanyIdAndRoleAndStatus(Long companyId, MemberRole role, MemberStatus status);

    // ===== 다른 기능의 삭제 가능 여부 검사 =====

    // 이 직급을 쓰는 회원이 있는지
    boolean existsByJobGradeId(Long JobGradeId);

    // 이 직책을 쓰는 회원이 있는지
    boolean existsByJobTitleId(Long JobTitleId);

    // 그 부서에 특정 상태가 아닌 회원이 있는지 (부서 삭제 시: RESIGNED 제외 = 재직자)
    boolean existsByDepartmentIdAndStatusNot(Long departmentId, MemberStatus status);

}
