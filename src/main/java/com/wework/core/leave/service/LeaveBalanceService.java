package com.wework.core.leave.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    private static final BigDecimal MAX_GRANTED_DAYS = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MIN_YEAR = 2015;

    public LeaveBalanceResponse getMyBalance(Long companyId, Long memberId, int year) {
        return leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year)
                .map(balance -> LeaveBalanceResponse.of(
                        year,
                        balance.getGrantedDays(),
                        balance.getUsedDays(),
                        remainingDays(balance.getGrantedDays(), balance.getUsedDays()))
                )
                .orElseGet(() -> LeaveBalanceResponse.of(year, ZERO, ZERO, ZERO));
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

        return LeaveBalanceResponse.of(
                year,
                balance.getGrantedDays(),
                balance.getUsedDays(),
                remainingDays(balance.getGrantedDays(), balance.getUsedDays())
        );
    }

    static BigDecimal remainingDays(BigDecimal granted, BigDecimal used) {
        return granted.subtract(used);
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
