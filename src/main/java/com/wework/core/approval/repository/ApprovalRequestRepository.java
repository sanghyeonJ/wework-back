package com.wework.core.approval.repository;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStepStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    // 읽기 전용 조회 (상세 보기 등). version을 올리지 않는다
    Optional<ApprovalRequest> findByIdAndCompanyId(Long id, Long companyId);

    // 처리용 조회 (승인·반려·취소). 커밋할 때 version이 무조건 1 올라간다
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<ApprovalRequest> findForProcessByIdAndCompanyId(Long id, Long companyId);

    // 같은 대상에 이미 결재 요청이 있는지 검사
    boolean existsByCompanyIdAndTargetTypeAndTargetId(Long companyId, String targetType, Long targetId);

    // 일반 사원의 "내가 처리할 건"
    @Query(
            value = """
                select r from ApprovalRequest r
                join fetch r.requester
                where r.company.id = :companyId
                    and r.status = :requestStatus
                    and exists (
                        select 1 from ApprovalStep s
                        where s.request = r
                            and s.approver.id = :memberId
                            and s.status = :stepStatus
                    )
                order by r.id desc
            """,
            countQuery = """
                select count(r) from ApprovalRequest r
                where r.company.id = :companyId
                    and r.status = :requestStatus
                    and exists (
                        select 1 from ApprovalStep s
                        where s.request = r
                            and s.approver.id = :memberId
                            and s.status = :stepStatus
                    )
            """
    )
    Page<ApprovalRequest> findPendingByApprover(@Param("companyId") Long companyId,
                                                @Param("memberId") Long memberId,
                                                @Param("requestStatus") ApprovalRequestStatus requestStatus,
                                                @Param("stepStatus") ApprovalStepStatus stepStatus,
                                                Pageable pageable);

    // 기업관리자의 "내가 처리할 건" (회사 전체)
    @Query(
            value = """
                select r from ApprovalRequest r
                join fetch r.requester
                where r.company.id = :companyId
                    and r.status = :requestStatus
                    and r.requester.id <> :memberId
                order by r.id desc
            """,
            countQuery = """
                select count(r) from ApprovalRequest r
                where r.company.id = :companyId
                    and r.status = :requestStatus
                    and r.requester.id <> :memberId
            """
    )
    Page<ApprovalRequest> findPendingForAdmin(@Param("companyId") Long companyId,
                                              @Param("memberId") Long memberId,
                                              @Param("requestStatus") ApprovalRequestStatus requestStatus,
                                              Pageable pageable);

    // "내가 처리한 건"
    @Query(
            value = """
                select r from ApprovalRequest r
                join fetch r.requester
                where r.company.id = :companyId
                    and exists (
                        select 1 from ApprovalStep s
                        where s.request = r
                            and s.processedBy.id = :memberId
                            and s.status in :statuses
                    )
                order by r.id desc
            """,
            countQuery = """
                select count(r) from ApprovalRequest r
                where r.company.id = :companyId
                    and exists (
                        select 1 from ApprovalStep s
                        where s.request = r
                            and s.processedBy.id = :memberId
                            and s.status in :statuses
                    )
            """
    )
    Page<ApprovalRequest> findProcessedByMember(@Param("companyId") Long companyId,
                                                @Param("memberId") Long memberId,
                                                @Param("statuses") List<ApprovalStepStatus> statuses,
                                                Pageable pageable);
}
