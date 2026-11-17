package com.wework.core.leave.service;

import com.wework.core.leave.dto.LeaveGrantRuleResponse;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LeaveGrantRuleService {

    private final LeaveGrantRuleRepository leaveGrantRuleRepository;

    public List<LeaveGrantRuleResponse> getRules(Long companyId) {
        return leaveGrantRuleRepository.findAllByCompanyIdOrderByServiceYearAsc(companyId)
                .stream()
                .map(LeaveGrantRuleResponse::of)
                .toList();
    }

}
