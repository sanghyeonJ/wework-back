package com.wework.core.member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
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

    // 사원 목록 검색 (조건은 모두 선택, null이면 조건 무시)
    // keyword는 서비스에서 "%소문자%" 형태로 만들어서 넘긴다
    // 부서 조건: allDepartments가 true면 전체, false면 departmentIds 안의 부서만
    @Query(
            value = """
                    select m from Member m
                    left join fetch m.department d
                    left join fetch m.jobGrade
                    left join fetch m.jobTitle
                    where m.company.id = :companyId
                        and (:status is null or m.status = :status)
                        and (:allDepartments = true or d.id in :departmentIds)
                        and (:keyword is null
                            or lower(m.name) like :keyword
                            or lower(m.email) like :keyword)
                    """,
            countQuery = """
                    select count(m) from Member m
                    left join m.department d
                    where m.company.id = :companyId
                        and (:status is null or m.status = :status)
                        and (:allDepartments = true or d.id in :departmentIds)
                        and (:keyword is null
                            or lower(m.name) like :keyword
                            or lower(m.email) like :keyword)
                    """
    )
    Page<Member> search(@Param("companyId") Long companyId,
                        @Param("status") MemberStatus status,
                        @Param("allDepartments") boolean allDepartments,
                        @Param("departmentIds") Collection<Long> departmentIds,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    // ===== 다른 기능의 삭제 가능 여부 검사 =====

    // 이 직급을 쓰는 회원이 있는지
    boolean existsByJobGradeId(Long JobGradeId);

    // 이 직책을 쓰는 회원이 있는지
    boolean existsByJobTitleId(Long JobTitleId);

    // 그 부서에 특정 상태가 아닌 회원이 있는지 (부서 삭제 시: RESIGNED 제외 = 재직자)
    boolean existsByDepartmentIdAndStatusNot(Long departmentId, MemberStatus status);

    // 특정 날짜에 재직 중이던 사원 (근태 관리자 조회용)
    // 재직 = 입사일 <= 날짜 이고, (ACTIVE 이거나, 퇴사일 >= 날짜인 퇴사자). INVITED(미가입)는 제외
    @Query("select m from Member m left join fetch m.department " +
            "where m.company.id = :companyId " +
            "and (m.hireDate is null or m.hireDate <= :date) " +
            "and (m.status = com.wework.core.member.MemberStatus.ACTIVE " +
            "     or (m.status = com.wework.core.member.MemberStatus.RESIGNED and m.resignDate >= :date)) " +
            "and (:allDepartments = true or m.department.id in :departmentIds) " +
            "order by m.name asc, m.id asc")
    List<Member> findEmployedOn(@Param("companyId") Long companyId,
                                @Param("date") LocalDate date,
                                @Param("allDepartments") boolean allDepartments,
                                @Param("departmentIds") List<Long> departmentIds);

}
