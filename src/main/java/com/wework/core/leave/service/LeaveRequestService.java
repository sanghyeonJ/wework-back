package com.wework.core.leave.service;

import com.wework.core.approval.service.ApprovalService;
import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.dto.LeaveApplyRequest;
import com.wework.core.leave.dto.LeaveDetailResponse;
import com.wework.core.leave.dto.LeaveSummaryResponse;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveRequestRepository;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestService {

    private final LeaveDayService leaveDayService;
    private final LeaveBalanceService leaveBalanceService;
    private final ApprovalService approvalService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    static final String APPROVAL_TARGET_TYPE = "LEAVE";
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional
    public Long apply(Long companyId, Long memberId, LeaveApplyRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(MemberErrorCode.MEMBER_RESIGNED);
        }

        BigDecimal days = calculateApplyDays(companyId, request.getLeaveType(), request.getStartDate(), request.getEndDate());
        validateNoOverlap(companyId, memberId, request.getLeaveType(), request.getStartDate(), request.getEndDate());
        if (request.getLeaveType().isDeductible()) {
            validateBalance(companyId, memberId, request.getStartDate(), days);
        }

        LeaveRequest saved = leaveRequestRepository.save(
                LeaveRequest.create(company, member, request.getLeaveType(), request.getStartDate(), request.getEndDate(), days, request.getReason())
        );
        Long approvalRequestId = approvalService.createRequest(
                companyId,
                memberId,
                APPROVAL_TARGET_TYPE,
                saved.getId(),
                buildTitle(request.getStartDate(), request.getEndDate(), days, request.getLeaveType()),
                request.getApproverIds());
        saved.linkApproval(approvalRequestId);
        return saved.getId();
    }

    public Page<LeaveSummaryResponse> getMyRequests(Long companyId, Long memberId, int year, LeaveRequestStatus status, Pageable pageable) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        LeaveBalanceService.validateYear(year, LocalDate.now(ZoneId.of(company.getTimeZone())).getYear());

        List<LeaveRequestStatus> statuses;
        if (status == null) {
            statuses = List.of(LeaveRequestStatus.values());
        } else {
            statuses = List.of(status);
        }

        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE)
        );
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        return leaveRequestRepository.findMine(companyId, memberId, statuses, yearStart, yearEnd, safePageable)
                .map(LeaveSummaryResponse::of);
    }

    public LeaveDetailResponse getDetail(Long companyId, Long memberId, Long id) {
        LeaveRequest leave = leaveRequestRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new BusinessException(LeaveErrorCode.LEAVE_REQUEST_NOT_FOUND));

        Long approvalRequestId = leave.getApprovalRequestId();
        if (approvalRequestId == null || !approvalService.canView(companyId, memberId, approvalRequestId)) {
            throw new BusinessException(LeaveErrorCode.LEAVE_REQUEST_NOT_FOUND);
        }

        return LeaveDetailResponse.of(leave);
    }



    static String buildTitle(LocalDate start, LocalDate end, BigDecimal days, LeaveType type) {
        String label = type == LeaveType.SICK ? "병가 신청" : "휴가 신청";
        return label + " " + start + " ~ " + end + " (" + days.toPlainString() + "일)";
    }

    public BigDecimal calculateApplyDays(Long companyId, LeaveType leaveType, LocalDate start, LocalDate end) {
        BigDecimal days = leaveDayService.calculateDays(companyId, leaveType, start, end);
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(LeaveErrorCode.NO_WORKING_DAY);
        }

        return days;
    }

    private void validateBalance(Long companyId, Long memberId, LocalDate start, BigDecimal applyDays) {
        int year = start.getYear();
        BigDecimal remaining = leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year)
                .map(balance -> LeaveBalanceService.remainingDays(balance.getGrantedDays(), balance.getUsedDays()))
                .orElse(BigDecimal.ZERO);

        BigDecimal pending = leaveBalanceService.pendingDays(companyId, memberId, year);
        if (!isBalanceEnough(remaining, pending, applyDays)) {
            throw new BusinessException(LeaveErrorCode.INSUFFICIENT_LEAVE_BALANCE);
        }
    }

    static boolean isBalanceEnough(BigDecimal remaining, BigDecimal pending, BigDecimal applyDays) {
        BigDecimal available = remaining.subtract(pending);
        return available.compareTo(applyDays) >= 0;
    }

    static boolean isOverlapAllowed(LeaveType newType, List<LeaveRequest> overlapping) {
        if (overlapping.isEmpty()) {
            return true;
        }

        if (!newType.isHalfDay()) {
            return false;
        }

        if (overlapping.size() != 1) {
            return false;
        }
        LeaveType existingType = overlapping.get(0).getLeaveType();
        if (!existingType.isHalfDay()) {
            return false;
        }
        if (newType == existingType) {
            return false;
        }
        return true;
    }

    private void validateNoOverlap(Long companyId, Long memberId, LeaveType leaveType, LocalDate start, LocalDate end) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                companyId, memberId, List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED), start, end
        );
        if (!isOverlapAllowed(leaveType, overlapping)) {
            throw new BusinessException(LeaveErrorCode.LEAVE_OVERLAPPED);
        }
    }

}
