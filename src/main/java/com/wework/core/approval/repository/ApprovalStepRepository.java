package com.wework.core.approval.repository;

import com.wework.core.approval.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    // 한 결재 요청의 단계 목록 (1단계부터 순서대로)
    List<ApprovalStep> findAllByRequestIdAndCompanyIdOrderByStepAsc(Long requestId, Long companyId);

}
