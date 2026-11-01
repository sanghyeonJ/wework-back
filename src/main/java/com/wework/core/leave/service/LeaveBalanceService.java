package com.wework.core.leave.service;

import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;

    public LeaveBalanceResponse getMyBalance(Long companyId, Long memberId, int year) {
        return leaveBalanceRepository.findByCompanyIdAndMemberIdAndYear(companyId, memberId, year)
                .map(balance -> LeaveBalanceResponse.of(
                        year,
                        balance.getGrantedDays(),
                        balance.getUsedDays(),
                        remainingDays(balance.getGrantedDays(), balance.getUsedDays()))
                )
                .orElseGet(() -> LeaveBalanceResponse.of(year, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    static BigDecimal remainingDays(BigDecimal granted, BigDecimal used) {
        return granted.subtract(used);
    }

}
