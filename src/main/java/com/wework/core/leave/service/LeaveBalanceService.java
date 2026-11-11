package com.wework.core.leave.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.dto.LeaveBalanceMemberResponse;
import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveRequestRepository;
import com.wework.core.leave.repository.PendingDaysSum;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import com.wework.global.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    private static final BigDecimal MAX_GRANTED_DAYS = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MIN_YEAR = 2015;
    private static final Long UNUSED_DEPARTMENT_ID = 0L;
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<LeaveType> DEDUCTIBLE_TYPES = List.of(LeaveType.ANNUAL, LeaveType.HALF_AM, LeaveType.HALF_PM);

    public LeaveBalanceResponse getMyBalance(Long companyId, Long memberId, int year) {
        return leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year)
                .map(balance -> toBalanceResponse(year, balance, pendingDays(companyId, memberId, year)))
                .orElseGet(() -> LeaveBalanceResponse.of(year, ZERO, ZERO, ZERO, ZERO));
    }

    @Transactional
    public LeaveBalanceResponse saveBalance(Long companyId, Long memberId, int year, BigDecimal grantedDays) {
        validateGrantedDays(grantedDays);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        ZoneId zoneId = ZoneId.of(company.getTimeZone());
        int currentYear = LocalDate.now(zoneId).getYear();
        validateYear(year, currentYear);

        Member member = memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(MemberErrorCode.MEMBER_RESIGNED);
        }

        Optional<LeaveBalance> found =
                leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year);
        LeaveBalance balance;
        if (found.isPresent()) {
            balance = found.get();
            if (grantedDays.compareTo(balance.getUsedDays()) < 0) {
                throw new BusinessException(LeaveErrorCode.GRANTED_DAYS_BELOW_USED);
            }
            balance.changeGrantedDays(grantedDays);
        } else {
            balance = leaveBalanceRepository.save(LeaveBalance.create(company, member, year, grantedDays));
        }

        return toBalanceResponse(year, balance, pendingDays(companyId, memberId, year));
    }

    public Page<LeaveBalanceMemberResponse> listBalances(Long companyId, int year, Pageable pageable) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));
        ZoneId zoneId = ZoneId.of(company.getTimeZone());

        Pageable fixedPageable = PageUtils.limit(pageable);
        Page<Member> members = memberRepository.findEmployedOn(companyId, LocalDate.now(zoneId), true, List.of(UNUSED_DEPARTMENT_ID), fixedPageable);

        List<Long> memberIds = members.stream().map(Member::getId).toList();
        Map<Long, LeaveBalance> balanceMap;
        Map<Long, BigDecimal> pendingMap;

        if (memberIds.isEmpty()) {
            balanceMap = Map.of();
            pendingMap = Map.of();
        } else {
            balanceMap = leaveBalanceRepository.findAllByCompanyIdAndYearAndMemberIdIn(companyId, year, memberIds)
                    .stream()
                    .collect(Collectors.toMap(balance -> balance.getMember().getId(), balance -> balance));
            pendingMap = leaveRequestRepository.sumDaysByMembers(companyId, memberIds, LeaveRequestStatus.PENDING, DEDUCTIBLE_TYPES, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                    .stream()
                    .collect(Collectors.toMap(PendingDaysSum::getMemberId, PendingDaysSum::getTotalDays));
        }

        return members.map(member -> LeaveBalanceMemberResponse.of(
                member.getId(),
                member.getName(),
                departmentNameOf(member),
                toBalanceResponse(year, balanceMap.get(member.getId()), pendingMap.getOrDefault(member.getId(), ZERO))
        ));
    }

    public BigDecimal pendingDays(Long companyId, Long memberId, int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        BigDecimal days = leaveRequestRepository.sumDays(companyId, memberId, LeaveRequestStatus.PENDING, DEDUCTIBLE_TYPES, startDate, endDate);
        if (days == null) {
            return ZERO;
        }
        return days;
    }


    static BigDecimal remainingDays(BigDecimal granted, BigDecimal used) {
        return granted.subtract(used);
    }

    private static LeaveBalanceResponse toBalanceResponse(int year, LeaveBalance balance, BigDecimal pendingDays) {
        if (balance == null) {
            return null;
        } else {
            return LeaveBalanceResponse.of(
                    year,
                    balance.getGrantedDays(),
                    balance.getUsedDays(),
                    remainingDays(balance.getGrantedDays(), balance.getUsedDays()),
                    pendingDays
            );
        }
    }

    private static String departmentNameOf(Member member) {
        if (member.getDepartment() == null) {
            return null;
        } else {
            return member.getDepartment().getName();
        }
    }

    static void validateGrantedDays(BigDecimal grantedDays) {
        if (grantedDays.compareTo(ZERO) < 0 || grantedDays.compareTo(MAX_GRANTED_DAYS) > 0) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_DAYS);
        }
        if ( grantedDays.multiply(BigDecimal.valueOf(2)).remainder(BigDecimal.ONE).compareTo(ZERO) != 0 ) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_DAYS);
        }
    }

    static void validateYear(int year, int currentYear) {
        if (year < MIN_YEAR || year > currentYear + 1) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_YEAR);
        }
    }

}
