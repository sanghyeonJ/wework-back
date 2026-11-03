package com.wework.core.approval.repository;

import com.wework.core.approval.entity.ApprovalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<Long, ApprovalRequest> {

    // 읽기 전용 조회 (상세 보기 등). version을 올리지 않는다
    Optional<ApprovalRequest> findByIdAndCompanyId(Long id, Long companyId);

    // 처리용 조회 (승인·반려·취소). 커밋할 때 version이 무조건 1 올라간다
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<ApprovalRequest> findForProcessByIdAndCompanyId(Long id, Long companyId);

    // 같은 대상에 이미 결재 요청이 있는지 검사
    boolean existsByCompanyIdAndTargetTypeAndTargetId(Long companyId, String targetType, Long targetId);

}
