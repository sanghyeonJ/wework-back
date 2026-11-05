package com.wework.core.approval;

import com.wework.core.approval.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@MockitoSpyBean(types = ApprovalRequestRepository.class)
class ApprovalLockUsageTest extends ApprovalTestSupport {

    // 버그 예방: 처리 메서드가 잠금 없는 findByIdAndCompanyId로 바뀌면 동시 처리 방어가 조용히 꺼진다
    @Test
    void 승인_반려_취소는_잠금_조회를_쓴다() {
        Long a = createApproval(requester, kim);
        Long b = createApproval(requester, kim);
        Long c = createApproval(requester, kim);

        approvalService.approve(company.getId(), kim.getId(), a, null);
        approvalService.reject(company.getId(), kim.getId(), b, null);
        approvalService.cancel(company.getId(), requester.getId(), c, null);

        verify(approvalRequestRepository).findForProcessByIdAndCompanyId(a, company.getId());
        verify(approvalRequestRepository).findForProcessByIdAndCompanyId(b, company.getId());
        verify(approvalRequestRepository).findForProcessByIdAndCompanyId(c, company.getId());
        verify(approvalRequestRepository, never()).findByIdAndCompanyId(any(), any());
    }

    // 상세를 열어보기만 했는데 version이 올라가면 처리 중인 다른 사람이 충돌을 만난다
    @Test
    void 상세_조회는_잠금_없는_읽기용_조회를_쓴다() {
        Long id = createApproval(requester, kim);

        approvalService.getDetail(company.getId(), admin.getId(), id);

        verify(approvalRequestRepository).findByIdAndCompanyId(id, company.getId());
        verify(approvalRequestRepository, never()).findForProcessByIdAndCompanyId(any(), any());
    }

    @Test
    void 요청_생성은_두_조회_모두_부르지_않는다() {
        approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L, "생성", java.util.List.of(kim.getId()));

        verify(approvalRequestRepository, never()).findByIdAndCompanyId(any(), any());
        verify(approvalRequestRepository, never()).findForProcessByIdAndCompanyId(any(), any());
    }
}