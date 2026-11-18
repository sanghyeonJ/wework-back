package com.wework.core.leave.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveGrantRule;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveGrantService {

    private final static int MIN_GRANT_SERVICE_YEARS = 1;

    private final LeaveGrantRuleRepository leaveGrantRuleRepository;
    private final MemberRepository memberRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final CompanyRepository companyRepository;


    @Transactional
    public int grantForCompany(Long companyId, int grantYear, LocalDate today) {
        List<LeaveGrantRule> rules = leaveGrantRuleRepository.findAllByCompanyIdOrderByServiceYearAsc(companyId);
        if (rules.isEmpty()) {
            return 0;
        }

        List<Member> members = memberRepository.findEmployedWithHireDate(companyId, today, MemberStatus.ACTIVE, MemberStatus.RESIGNED);
        if (members.isEmpty()) {
            return 0;
        }

        List<Long> memberIds = members.stream().map(Member::getId).toList();
        Set<Long> alreadyGranted = leaveBalanceRepository
                .findAllByCompanyIdAndYearAndMemberIdIn(companyId, grantYear, memberIds)
                .stream()
                .map(balance -> balance.getMember().getId())
                .collect(Collectors.toSet());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));

        List<LeaveBalance> newBalances = new ArrayList<>();
        for (Member member : members) {
            if (alreadyGranted.contains(member.getId())) {
                continue;
            }
            int serviceYear = calculateServiceYears(member.getHireDate(), grantYear);
            Optional<BigDecimal> days = findGrantedDays(rules, serviceYear);
            if (days.isEmpty()) {
                continue;
            }
            newBalances.add(LeaveBalance.create(company, member, grantYear, days.get()));
        }

        leaveBalanceRepository.saveAll(newBalances);
        return newBalances.size();
    }



    static int calculateServiceYears(LocalDate hireDate, int grantYear) {
        return grantYear - hireDate.getYear();
    }

    static Optional<BigDecimal> findGrantedDays(List<LeaveGrantRule> rules, int serviceYears) {
        if (serviceYears < MIN_GRANT_SERVICE_YEARS) {
            return Optional.empty();
        }

        LeaveGrantRule best = null;
        for (LeaveGrantRule rule: rules) {
            if (rule.getServiceYear() <= serviceYears) {
                if (best == null || rule.getServiceYear() > best.getServiceYear()) {
                    best = rule;
                }
            }
        }

        return Optional.ofNullable(best).map(LeaveGrantRule::getGrantedDays);
    }

}
