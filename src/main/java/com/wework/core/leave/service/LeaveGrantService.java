package com.wework.core.leave.service;

import com.wework.core.leave.entity.LeaveGrantRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveGrantService {

    private final static int MIN_GRANT_SERVICE_YEARS = 1;

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
