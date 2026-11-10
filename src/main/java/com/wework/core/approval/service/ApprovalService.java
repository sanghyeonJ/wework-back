package com.wework.core.approval.service;

import com.wework.core.approval.dto.ApprovalDetailResponse;
import com.wework.core.approval.dto.ApprovalSummaryResponse;
import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.approval.entity.ApprovalStepStatus;
import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.core.approval.event.ApprovalCompletedEvent;
import com.wework.core.approval.repository.ApprovalRequestRepository;
import com.wework.core.approval.repository.ApprovalStepRepository;
import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private final MemberRepository memberRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final CompanyRepository companyRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final int MAX_APPROVER_COUNT = 5;
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional
    public Long createRequest(Long companyId, Long requesterId, String targetType, Long targetId, String title, List<Long> approverIds) {
        validateApproverIds(requesterId, approverIds);
        Member requester = memberRepository.findByIdAndCompanyId(requesterId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        List<Member> approvers = loadApprovers(companyId, approverIds);
        if (approvalRequestRepository.existsByCompanyIdAndTargetTypeAndTargetId(companyId, targetType, targetId)) {
            throw new BusinessException(ApprovalErrorCode.APPROVAL_ALREADY_EXISTS);
        }

        Company company = companyRepository.getReferenceById(companyId);
        ApprovalRequest requestEntity = ApprovalRequest.create(
                company,
                requester,
                targetType,
                targetId,
                title
        );

        ApprovalRequest savedRequest = approvalRequestRepository.save(requestEntity);
        List<ApprovalStep> steps = new ArrayList<>();
        for (int i = 0; i < approvers.size(); i++) {
            ApprovalStep step = ApprovalStep.create(company, savedRequest, i + 1, approvers.get(i));
            steps.add(step);
        }
        approvalStepRepository.saveAll(steps);

        return savedRequest.getId();
    }

    @Transactional
    public void approve(Long companyId, Long memberId, Long requestId, String comment) {
        ProcessContext processContext = loadProcessContext(companyId, memberId, requestId);
        Member member = processContext.getMember();
        ApprovalRequest request = processContext.getRequest();
        List<ApprovalStep> steps = processContext.getSteps();
        ApprovalStep currentStep = processContext.getCurrentStep();
        int currentIndex = processContext.getCurrentIndex();
        boolean proxy = processContext.isProxy();

        Instant now = Instant.now();
        currentStep.approve(member, comment, now);
        if (!proxy) {
            if (currentIndex + 1 < steps.size()) {
                steps.get(currentIndex + 1).activate();
            } else {
                request.approve();
                publishCompletedEvent(companyId, request, ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING);
            }
        } else {
            for (int i = currentIndex + 1; i < steps.size(); i++){
                steps.get(i).skip(member, now);
            }
            request.approve();
            publishCompletedEvent(companyId, request, ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING);
        }
    }

    @Transactional
    public void reject(Long companyId, Long memberId, Long requestId, String comment) {
        ProcessContext processContext = loadProcessContext(companyId, memberId, requestId);
        Member member = processContext.getMember();
        ApprovalRequest request = processContext.getRequest();
        List<ApprovalStep> steps = processContext.getSteps();
        ApprovalStep currentStep = processContext.getCurrentStep();
        int currentIndex = processContext.getCurrentIndex();

        Instant now = Instant.now();
        currentStep.reject(member, comment, now);
        request.reject();
        for (int i = currentIndex + 1; i < steps.size(); i++) {
            steps.get(i).cancel();
        }
        publishCompletedEvent(companyId, request, ApprovalRequestStatus.REJECTED, ApprovalRequestStatus.PENDING);
    }

    @Transactional
    public void cancel(Long companyId, Long memberId, Long requestId, String reason) {
        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        ApprovalRequest request = approvalRequestRepository.findForProcessByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new BusinessException(ApprovalErrorCode.APPROVAL_NOT_FOUND));

        if (request.getStatus() == ApprovalRequestStatus.CANCELED || request.getStatus() == ApprovalRequestStatus.REJECTED) {
            throw new BusinessException(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED);
        }
        boolean isOwner = member.getId().equals(request.getRequester().getId());
        boolean isAdmin = member.getRole() == MemberRole.COMPANY_ADMIN;
        if (isOwner) {
            if (request.getStatus() != ApprovalRequestStatus.PENDING) {
                throw new BusinessException(ApprovalErrorCode.CANCEL_NOT_ALLOWED);
            }
        } else if (isAdmin) {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(ApprovalErrorCode.CANCEL_REASON_REQUIRED);
            }
        } else {
            throw new BusinessException(ApprovalErrorCode.NOT_APPROVER);
        }

        ApprovalRequestStatus beforeStatus = request.getStatus();
        Instant now = Instant.now();
        request.cancel(member, now, reason);

        List<ApprovalStep> steps = approvalStepRepository.findAllByRequestIdAndCompanyIdOrderByStepAsc(requestId, companyId);
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStatus() == ApprovalStepStatus.PENDING || steps.get(i).getStatus() == ApprovalStepStatus.WAITING) {
                steps.get(i).cancel();
            }
        }
        publishCompletedEvent(companyId, request, ApprovalRequestStatus.CANCELED, beforeStatus);
    }

    public Page<ApprovalSummaryResponse> getPendingList(Long companyId, Long memberId, Pageable pageable) {
        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)
        );

        if (member.getRole() == MemberRole.COMPANY_ADMIN) {
            Page<ApprovalRequest> requests = approvalRequestRepository.findPendingForAdmin(
                    companyId, member.getId(), ApprovalRequestStatus.PENDING, safePageable
            );
            return requests.map(ApprovalSummaryResponse::of);
        } else {
            Page<ApprovalRequest> requests = approvalRequestRepository.findPendingByApprover(
                    companyId, member.getId(), ApprovalRequestStatus.PENDING, ApprovalStepStatus.PENDING, safePageable
            );
            return requests.map(ApprovalSummaryResponse::of);
        }

    }

    public Page<ApprovalSummaryResponse> getProcessedList(Long companyId, Long memberId, Pageable pageable) {
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)
        );
        List<ApprovalStepStatus> statuses = List.of(ApprovalStepStatus.APPROVED, ApprovalStepStatus.REJECTED);

        Page<ApprovalRequest> requests = approvalRequestRepository.findProcessedByMember(companyId, memberId, statuses, safePageable);
        return requests.map(ApprovalSummaryResponse::of);
    }

    public ApprovalDetailResponse getDetail(Long companyId, Long memberId, Long requestId) {
        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        ApprovalRequest request = approvalRequestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new BusinessException(ApprovalErrorCode.APPROVAL_NOT_FOUND));

        List<ApprovalStep> steps = approvalStepRepository.findAllByRequestIdAndCompanyIdOrderByStepAsc(requestId, companyId);

        boolean canView = isViewer(member, request, steps);

        if (canView) {
            return ApprovalDetailResponse.of(request, steps);
        } else {
            throw new BusinessException(ApprovalErrorCode.APPROVAL_NOT_FOUND);
        }

    }



    static boolean isViewer(Member member, ApprovalRequest request, List<ApprovalStep> steps) {
        boolean canView = false;
        if (member.getRole() == MemberRole.COMPANY_ADMIN || request.getRequester().getId().equals(member.getId())) {
            canView = true;
        }
        if (!canView) {
            for (ApprovalStep step : steps) {
                if (step.getApprover().getId().equals(member.getId())) {
                    canView = true;
                    break;
                }
            }
        }

        return canView;
    }

    static void validateApproverIds(Long requesterId, List<Long> approverIds) {
        if (approverIds == null || approverIds.isEmpty() || approverIds.size() > MAX_APPROVER_COUNT) {
            throw new BusinessException(ApprovalErrorCode.INVALID_APPROVER_COUNT);
        }

        Set<Long> checkedIds = new HashSet<>();
        for (Long target : approverIds) {
            if (target == null || target.equals(requesterId) || !checkedIds.add(target)) {
                throw new BusinessException(ApprovalErrorCode.INVALID_APPROVER);
            }
        }
    }

    private void publishCompletedEvent(Long companyId, ApprovalRequest request, ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        ApprovalCompletedEvent event = ApprovalCompletedEvent.of(request.getId(), companyId, request.getTargetType(), request.getTargetId(), result, previousStatus);
        applicationEventPublisher.publishEvent(event);
    }

    private ProcessContext loadProcessContext(Long companyId, Long memberId, Long requestId) {
        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        ApprovalRequest request = approvalRequestRepository.findForProcessByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new BusinessException(ApprovalErrorCode.APPROVAL_NOT_FOUND));

        if (request.getStatus() != ApprovalRequestStatus.PENDING) {
            throw new BusinessException(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED);
        }
        if (request.getRequester().getId().equals(member.getId())) {
            throw new BusinessException(ApprovalErrorCode.CANNOT_PROCESS_OWN_REQUEST);
        }

        List<ApprovalStep> steps = approvalStepRepository.findAllByRequestIdAndCompanyIdOrderByStepAsc(requestId, companyId);
        ApprovalStep currentStep = null;
        int currentIndex = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStatus() == ApprovalStepStatus.PENDING) {
                currentStep = steps.get(i);
                currentIndex = i;
                break;
            }
        }
        if (currentStep == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        boolean isMyTurn = currentStep.getApprover().getId().equals(member.getId());
        boolean isAdmin = member.getRole() == MemberRole.COMPANY_ADMIN;
        boolean proxyApproval;
        if (isMyTurn) {
            proxyApproval = false;
        } else if (isAdmin) {
            proxyApproval = true;
        } else {
            for (ApprovalStep step :steps) {
                if (step.getApprover().getId().equals(member.getId())) {
                    throw new BusinessException(ApprovalErrorCode.NOT_YOUR_TURN);
                }
            }
            throw new BusinessException(ApprovalErrorCode.NOT_APPROVER);
        }

        return ProcessContext.of(member, request, steps, currentStep, currentIndex, proxyApproval);
    }

    private List<Member> loadApprovers(Long companyId, List<Long> approverIds) {
        List<Member> approverMembers = memberRepository.findAllByCompanyIdAndIdIn(companyId, approverIds);
        HashMap<Long, Member> memberMap = new HashMap<>();

        if (approverIds.size() != approverMembers.size()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        for (Member member : approverMembers) {
            if (member.getStatus() != MemberStatus.ACTIVE) {
                throw new BusinessException(ApprovalErrorCode.INVALID_APPROVER);
            }
            memberMap.put(member.getId(), member);
        }

        List<Member> result = new ArrayList<>();
        for (Long memberId : approverIds) {
            Member member = memberMap.get(memberId);
            result.add(member);
        }

        return result;

    }

    @Getter
    private static class ProcessContext {

        private final Member member;
        private final ApprovalRequest request;
        private final List<ApprovalStep> steps;
        private final ApprovalStep currentStep;
        private final int currentIndex;
        private final boolean proxy;

        private ProcessContext(Member member, ApprovalRequest request, List<ApprovalStep> steps, ApprovalStep currentStep, int currentIndex, boolean proxy) {
            this.member = member;
            this.request = request;
            this.steps = steps;
            this.currentStep = currentStep;
            this.currentIndex = currentIndex;
            this.proxy = proxy;
        }

        private static ProcessContext of(Member member, ApprovalRequest request, List<ApprovalStep> steps, ApprovalStep currentStep, int currentIndex, boolean proxy) {
            return new ProcessContext(member, request, steps, currentStep, currentIndex, proxy);
        }

    }
}