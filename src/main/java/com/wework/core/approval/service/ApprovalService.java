package com.wework.core.approval.service;

import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.error.MemberErrorCode;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private final MemberRepository memberRepository;

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

}
