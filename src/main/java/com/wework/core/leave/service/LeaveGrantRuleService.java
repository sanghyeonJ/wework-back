package com.wework.core.leave.service;

import com.wework.core.leave.dto.LeaveGrantRuleRequest;
import com.wework.core.leave.dto.LeaveGrantRuleResponse;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveGrantRuleService {

    private final LeaveGrantRuleRepository leaveGrantRuleRepository;

    private static final int MIN_SERVICE_YEAR = 1;
    private static final int MAX_SERVICE_YEAR = 50;

    public List<LeaveGrantRuleResponse> getRules(Long companyId) {
        return leaveGrantRuleRepository.findAllByCompanyIdOrderByServiceYearAsc(companyId)
                .stream()
                .map(LeaveGrantRuleResponse::of)
                .toList();
    }

    static void validateRules(List<LeaveGrantRuleRequest.Item> items) {
        Set<Integer> seenYears = new HashSet<>();
        for (LeaveGrantRuleRequest.Item item : items) {
            if (item == null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            if (item.getServiceYear() < MIN_SERVICE_YEAR || item.getServiceYear() > MAX_SERVICE_YEAR) {
                throw new BusinessException(LeaveErrorCode.INVALID_SERVICE_YEAR);
            }
            if (!seenYears.add(item.getServiceYear())) {
                throw new BusinessException(LeaveErrorCode.DUPLICATE_SERVICE_YEAR);
            }
            LeaveBalanceService.validateGrantedDays(item.getGrantedDays());
        }
    }

}
