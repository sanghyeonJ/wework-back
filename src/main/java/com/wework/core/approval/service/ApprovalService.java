package com.wework.core.approval.service;

import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private static final int MAX_APPROVER_COUNT = 5;

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

}
