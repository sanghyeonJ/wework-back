package com.wework.core.approval.service;

import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.global.error.BusinessException;
import com.wework.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApprovalServiceValidationTest {

    private static final Long REQUESTER_ID = 1L;

    private static void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException e = assertThrows(BusinessException.class, executable);
        assertEquals(expected, e.getErrorCode());
    }

    @Test
    void 결재자_1명과_5명은_통과한다() {
        assertDoesNotThrow(() -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of(2L)));
        assertDoesNotThrow(() -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of(2L, 3L, 4L, 5L, 6L)));
    }

    @Test
    void 결재자가_없거나_null이거나_6명이면_INVALID_APPROVER_COUNT() {
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER_COUNT,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, null));
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER_COUNT,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of()));
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER_COUNT,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of(2L, 3L, 4L, 5L, 6L, 7L)));
    }

    @Test
    void 같은_결재자를_두_번_넣으면_INVALID_APPROVER() {
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of(2L, 3L, 2L)));
    }

    @Test
    void 신청자_본인을_결재자로_넣으면_INVALID_APPROVER() {
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, List.of(2L, REQUESTER_ID)));
    }

    @Test
    void 결재자_목록_안에_null이_있으면_INVALID_APPROVER() {
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER,
                () -> ApprovalService.validateApproverIds(REQUESTER_ID, Arrays.asList(2L, null)));
    }

    // 버그 예방: Long을 == 로 비교하면 127 넘는 id에서 같은 값이어도 다른 객체라 본인 검사를 통과해 버린다
    @Test
    void 큰_id도_값이_같으면_본인과_중복으로_판단한다() {
        Long requesterId = 1000L;
        Long sameValue = 1000L;
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER,
                () -> ApprovalService.validateApproverIds(requesterId, List.of(sameValue)));

        Long first = 2000L;
        Long second = 2000L;
        assertErrorCode(ApprovalErrorCode.INVALID_APPROVER,
                () -> ApprovalService.validateApproverIds(requesterId, List.of(first, second)));
    }
}