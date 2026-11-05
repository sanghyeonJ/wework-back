package com.wework.core.approval;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.approval.entity.ApprovalStepStatus;
import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalCreateTest extends ApprovalTestSupport {

    @Test
    void 결재자는_요청한_순서대로_단계가_되고_첫_단계만_내_차례다() {
        Long id = approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L,
                "휴가 신청", List.of(park.getId(), kim.getId()));

        ApprovalRequest request = requestOf(id);
        assertEquals(ApprovalRequestStatus.PENDING, request.getStatus());
        assertEquals(requester.getId(), request.getRequester().getId());
        assertEquals(company.getId(), request.getCompany().getId());

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(2, steps.size());
        assertEquals(park.getId(), steps.get(0).getApprover().getId());
        assertEquals(ApprovalStepStatus.PENDING, steps.get(0).getStatus());
        assertEquals(kim.getId(), steps.get(1).getApprover().getId());
        assertEquals(ApprovalStepStatus.WAITING, steps.get(1).getStatus());
    }

    @Test
    void 결재자_5명은_만들어지고_6명은_거절된다() {
        List<Long> six = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            six.add(newEmployee("m" + i).getId());
        }

        Long id = approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L,
                "5명", six.subList(0, 5));
        assertEquals(5, stepsOf(id).size());

        long before = approvalRequestRepository.count();
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER_COUNT, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 2L, "6명", six));
        assertEquals(before, approvalRequestRepository.count());
    }

    @Test
    void 결재자가_비어_있으면_거절된다() {
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER_COUNT, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L, "없음", List.of()));
    }

    @Test
    void 중복_결재자와_본인_결재자는_거절되고_저장되지_않는다() {
        long before = approvalRequestRepository.count();

        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L,
                        "중복", List.of(kim.getId(), kim.getId())));
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 2L,
                        "본인", List.of(kim.getId(), requester.getId())));

        assertEquals(before, approvalRequestRepository.count());
    }

    @Test
    void 다른_회사_결재자가_섞이면_404이고_저장되지_않는다_내_회사_결재자만이면_성공한다() {
        long before = approvalRequestRepository.count();

        assertErrorCode(MemberErrorCode.MEMBER_NOT_FOUND, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L,
                        "섞임", List.of(otherKim.getId(), kim.getId())));
        assertEquals(before, approvalRequestRepository.count());

        Long id = approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 2L,
                "내 회사", List.of(kim.getId()));
        assertEquals(1, stepsOf(id).size());
    }

    @Test
    void 신청자가_다른_회사_사원이면_404() {
        assertErrorCode(MemberErrorCode.MEMBER_NOT_FOUND, () ->
                approvalService.createRequest(company.getId(), otherKim.getId(), "LEAVE", 1L,
                        "남의 사원", List.of(kim.getId())));
    }

    @Test
    void 퇴사자와_가입_전_사원은_결재자가_될_수_없다() {
        Member resigned = newEmployee("resigned");
        Member invited = newEmployee("invited");
        ReflectionTestUtils.setField(resigned, "status", MemberStatus.RESIGNED);
        ReflectionTestUtils.setField(invited, "status", MemberStatus.INVITED);

        long before = approvalRequestRepository.count();
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 1L,
                        "퇴사자", List.of(kim.getId(), resigned.getId())));
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 2L,
                        "가입 전", List.of(invited.getId())));
        assertEquals(before, approvalRequestRepository.count());
    }

    @Test
    void 같은_대상에_두_번_요청하면_409이고_다른_대상은_된다() {
        approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 5L, "첫 요청", List.of(kim.getId()));

        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_EXISTS, () ->
                approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 5L,
                        "두 번째", List.of(kim.getId())));

        Long other = approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 6L,
                "다른 대상", List.of(kim.getId()));
        assertEquals(1, stepsOf(other).size());
    }

    @Test
    void 대상_종류가_다르면_같은_대상_id여도_따로_만들_수_있다_아니_같은_종류의_같은_id만_막는다() {
        Long first = approvalService.createRequest(company.getId(), requester.getId(), "LEAVE", 7L,
                "휴가", List.of(kim.getId()));
        assertEquals(1, stepsOf(first).size());
    }
}