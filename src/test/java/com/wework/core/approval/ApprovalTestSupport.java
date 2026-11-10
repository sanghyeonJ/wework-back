package com.wework.core.approval;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.approval.event.ApprovalCompletedEvent;
import com.wework.core.approval.repository.ApprovalRequestRepository;
import com.wework.core.approval.repository.ApprovalStepRepository;
import com.wework.core.approval.service.ApprovalService;
import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.member.entity.Member;
import com.wework.global.error.BusinessException;
import com.wework.global.error.ErrorCode;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RecordApplicationEvents
public abstract class ApprovalTestSupport extends IntegrationTestSupport {

    @Autowired
    protected ApprovalService approvalService;
    @Autowired
    protected ApprovalRequestRepository approvalRequestRepository;
    @Autowired
    protected ApprovalStepRepository approvalStepRepository;
    @Autowired
    protected ApplicationEvents applicationEvents;

    protected Company company;
    protected Department department;
    protected Member admin;
    protected Member requester;   // 신청자 (최)
    protected Member kim;         // 결재자
    protected Member park;        // 결재자
    protected Member outsider;    // 같은 회사지만 결재선에 없는 사원

    protected Company otherCompany;
    protected Member otherAdmin;
    protected Member otherRequester;
    protected Member otherKim;

    private long nextTargetId = 1000L;

    @BeforeEach
    void setUpApprovalBase() {
        company = createCompany("결재 테스트 회사", "111-11-11111");
        department = createDepartment(company, null, "개발팀");
        admin = createAdmin(company, "admin@approval.test");
        requester = createEmployee(company, department, "choi@approval.test");
        kim = createEmployee(company, department, "kim@approval.test");
        park = createEmployee(company, department, "park@approval.test");
        outsider = createEmployee(company, department, "outsider@approval.test");

        otherCompany = createCompany("다른 결재 회사", "222-22-22222");
        var otherDepartment = createDepartment(otherCompany, null, "다른 개발팀");
        otherAdmin = createAdmin(otherCompany, "admin@other-approval.test");
        otherRequester = createEmployee(otherCompany, otherDepartment, "choi@other-approval.test");
        otherKim = createEmployee(otherCompany, otherDepartment, "kim@other-approval.test");
    }

    protected Member newEmployee(String name) {
        return createEmployee(company, department, name + "@approval.test");
    }

    /** 서비스를 거치지 않고 리포지토리로 결재 요청과 단계를 바로 만든다 (테스트 대상이 아닌 준비용) */
    protected Long createApproval(Member applicant, Member... approvers) {
        return createApproval(company, applicant, approvers);
    }

    protected Long createApproval(Company targetCompany, Member applicant, Member... approvers) {
        ApprovalRequest request = approvalRequestRepository.save(
                ApprovalRequest.create(targetCompany, applicant, "TEST", nextTargetId++, "테스트 결재"));
        List<ApprovalStep> steps = new ArrayList<>();
        for (int i = 0; i < approvers.length; i++) {
            steps.add(ApprovalStep.create(targetCompany, request, i + 1, approvers[i]));
        }
        approvalStepRepository.saveAll(steps);
        return request.getId();
    }

    protected ApprovalRequest requestOf(Long requestId) {
        return approvalRequestRepository.findById(requestId).orElseThrow();
    }

    protected List<ApprovalStep> stepsOf(Long requestId) {
        return approvalStepRepository.findAllByRequestIdAndCompanyIdOrderByStepAsc(requestId, company.getId());
    }

    protected List<ApprovalCompletedEvent> completedEvents() {
        return applicationEvents.stream(ApprovalCompletedEvent.class).toList();
    }

    protected void assertEvent(ApprovalCompletedEvent event, Long requestId,
                               ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        ApprovalRequest request = requestOf(requestId);
        assertEquals(requestId, event.getRequestId());
        assertEquals(company.getId(), event.getCompanyId());
        assertEquals(request.getTargetType(), event.getTargetType());
        assertEquals(request.getTargetId(), event.getTargetId());
        assertEquals(result, event.getResult());
        assertEquals(previousStatus, event.getPreviousStatus());
    }

    protected static void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException e = assertThrows(BusinessException.class, executable);
        assertEquals(expected, e.getErrorCode());
    }
}