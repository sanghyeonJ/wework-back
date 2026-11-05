package com.wework.core.approval;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.service.ApprovalService;
import com.wework.core.company.entity.Company;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 테스트 전체가 한 트랜잭션이라 실제 동시 처리는 만들 수 없다.
 * 서비스를 가짜로 바꿔 "충돌 예외가 올라오면 응답이 409인가"만 확인한다.
 */
class ApprovalConflictTest extends IntegrationTestSupport {

    @MockitoBean
    ApprovalService approvalService;

    @Test
    void 낙관적_락_충돌은_500이_아니라_409_CONCURRENT_MODIFICATION으로_응답한다() throws Exception {
        Company company = createCompany("충돌 테스트 회사", "333-33-33333");
        Member admin = createAdmin(company, "admin@conflict.test");

        ObjectOptimisticLockingFailureException conflict =
                new ObjectOptimisticLockingFailureException(ApprovalRequest.class, 1L);
        doThrow(conflict).when(approvalService).approve(any(), any(), any(), any());
        doThrow(conflict).when(approvalService).reject(any(), any(), any(), any());
        doThrow(conflict).when(approvalService).cancel(any(), any(), any(), any());

        for (String action : List.of("approve", "reject", "cancel")) {
            mockMvc.perform(post("/api/approvals/{id}/{action}", 1L, action)
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }
    }
}