package com.wework.core.approval;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStepStatus;
import com.wework.core.member.entity.Member;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovalApiTest extends ApprovalTestSupport {

    private ResultActions act(String action, Member who, Long id, String body) throws Exception {
        return mockMvc.perform(post("/api/approvals/{id}/{action}", id, action)
                .header("Authorization", bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions read(String url, Member who, Object... vars) throws Exception {
        return mockMvc.perform(get(url, vars).header("Authorization", bearer(who)));
    }

    private String comment(String text) {
        return "{\"comment\":\"" + text + "\"}";
    }

    // ---------------- 격리 ----------------

    @Test
    void 다른_회사_결재는_상세도_처리도_404이고_내_회사는_성공한다() throws Exception {
        Long id = createApproval(requester, kim, park);

        read("/api/approvals/{id}", otherAdmin, id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
        for (String action : List.of("approve", "reject", "cancel")) {
            act(action, otherAdmin, id, "{}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
        }
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());

        read("/api/approvals/{id}", admin, id).andExpect(status().isOk());
        act("approve", kim, id, "{}").andExpect(status().isOk());
        assertEquals(ApprovalStepStatus.APPROVED, stepsOf(id).get(0).getStatus());
    }

    // 버그 예방: 쿼리에 회사 id와 내 id를 거꾸로 넘기면 다른 회사의 결재가 보이거나 내 결재가 안 보인다
    @Test
    void 목록에_다른_회사_결재가_섞이지_않는다() throws Exception {
        Long mine = createApproval(requester, kim);
        Long other = createApproval(otherCompany, otherRequester, otherKim);

        read("/api/approvals/pending", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
        read("/api/approvals/pending", otherAdmin)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(other));
        read("/api/approvals/pending", kim)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
        read("/api/approvals/pending", otherKim)
                .andExpect(jsonPath("$.content[0].id").value(other));
    }

    // ---------------- 처리할 건 ----------------

    @Test
    void 일반_사원의_처리할_건은_지금_내_차례인_결재만_최신순으로_나온다() throws Exception {
        Long a = createApproval(requester, kim, park);
        Long b = createApproval(requester, park, kim);

        read("/api/approvals/pending", kim)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a));
        read("/api/approvals/pending", park)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));

        act("approve", kim, a, "{}").andExpect(status().isOk());

        read("/api/approvals/pending", park)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(b))
                .andExpect(jsonPath("$.content[1].id").value(a));
        // B는 park가 먼저라서 kim은 아직 내 차례가 아니다
        read("/api/approvals/pending", kim)
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // park가 B를 승인하면 차례가 kim에게 넘어온다
        act("approve", park, b, "{}").andExpect(status().isOk());
        read("/api/approvals/pending", kim)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));

        read("/api/approvals/pending", outsider)
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void 기업관리자의_처리할_건은_회사_전체_진행_중_결재에서_본인_신청_건만_빠진다() throws Exception {
        Long byAdmin = createApproval(admin, kim);
        Long byRequester = createApproval(requester, kim);
        Long done = createApproval(requester, park);
        act("approve", park, done, "{}").andExpect(status().isOk());

        read("/api/approvals/pending", admin)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(byRequester));
        // 짝: 같은 결재를 지정 결재자(kim)는 둘 다 볼 수 있다
        read("/api/approvals/pending", kim)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(byRequester))
                .andExpect(jsonPath("$.content[1].id").value(byAdmin));
    }

    // ---------------- 처리한 건 ----------------

    // 버그 예방: countQuery의 조건이 본문과 다르면 목록은 나와도 전체 건수가 틀린다
    @Test
    void 처리한_건은_내가_승인_반려한_결재만_중복_없이_나오고_취소와_건너뜀은_제외된다() throws Exception {
        Long a = createApproval(requester, kim, park);
        Long b = createApproval(requester, kim, park);
        Long c = createApproval(requester, kim, park);
        Long d = createApproval(requester, kim, park);

        act("approve", kim, a, "{}").andExpect(status().isOk());
        act("reject", kim, b, "{}").andExpect(status().isOk());
        act("approve", admin, c, "{}").andExpect(status().isOk());
        act("cancel", admin, d, comment("cancel reason")).andExpect(status().isOk());

        read("/api/approvals/processed", kim)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(b))
                .andExpect(jsonPath("$.content[1].id").value(a));
        read("/api/approvals/processed", admin)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c));
        read("/api/approvals/processed", park)
                .andExpect(jsonPath("$.page.totalElements").value(0));
        read("/api/approvals/processed", requester)
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------------- 상세 ----------------

    @Test
    void 상세는_신청자_결재자_기업관리자만_볼_수_있다() throws Exception {
        Long id = createApproval(requester, kim, park);

        for (Member viewer : List.of(requester, kim, park, admin)) {
            read("/api/approvals/{id}", viewer, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));
        }
        for (Member stranger : List.of(outsider, otherKim)) {
            read("/api/approvals/{id}", stranger, id)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
        }
    }

    // 버그 예방: 취소하지 않은 결재의 canceledBy가 null이라 이름을 꺼내다 500이 나던 실수
    @Test
    void 진행_중_상세에는_단계가_순서대로_있고_취소_정보와_처리자는_비어_있다() throws Exception {
        Long id = createApproval(requester, kim, park);

        read("/api/approvals/{id}", admin, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].status").value("PENDING"))
                .andExpect(jsonPath("$.steps[1].status").value("WAITING"))
                .andExpect(jsonPath("$.steps[0].processedByName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.canceledByName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.canceledAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.cancelReason").value(Matchers.nullValue()));
    }

    @Test
    void 대리_승인_뒤_상세에는_승인과_건너뜀_처리자가_보인다() throws Exception {
        Long id = createApproval(requester, kim, park);
        act("approve", admin, id, "{}").andExpect(status().isOk());

        read("/api/approvals/{id}", requester, id)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.steps[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.steps[0].processedByName").isNotEmpty())
                .andExpect(jsonPath("$.steps[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.steps[1].processedByName").isNotEmpty());
    }

    @Test
    void 취소_뒤_상세에는_취소자_시각_사유가_보인다() throws Exception {
        Long id = createApproval(requester, kim, park);
        act("cancel", admin, id, comment("late request")).andExpect(status().isOk());

        read("/api/approvals/{id}", requester, id)
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancelReason").value("late request"))
                .andExpect(jsonPath("$.canceledByName").isNotEmpty())
                .andExpect(jsonPath("$.canceledAt").isNotEmpty())
                .andExpect(jsonPath("$.steps[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.steps[1].status").value("CANCELED"));
    }

    // ---------------- 페이징 ----------------

    @Test
    void 처리할_건_목록의_페이징_범위_밖_페이지_size_상한_sort_무시() throws Exception {
        createApproval(requester, kim);
        createApproval(requester, kim);
        Long newest = createApproval(requester, kim);

        read("/api/approvals/pending?size=1&page=0", admin)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.page.size").value(1));
        read("/api/approvals/pending?size=1&page=3", admin)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        read("/api/approvals/pending?size=1000", admin)
                .andExpect(jsonPath("$.page.size").value(100));
        read("/api/approvals/pending?sort=id,asc", admin)
                .andExpect(jsonPath("$.content[0].id").value(newest));
    }

    // ---------------- 입력 검사 ----------------

    @Test
    void comment는_500자까지_허용하고_501자부터_400이다() throws Exception {
        Long id = createApproval(requester, kim);

        act("approve", kim, id, comment("a".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());

        act("approve", kim, id, comment("a".repeat(500))).andExpect(status().isOk());
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
    }

    @Test
    void 본문이_아예_없으면_400이고_빈_JSON은_통과한다() throws Exception {
        Long id = createApproval(requester, kim);

        mockMvc.perform(post("/api/approvals/{id}/approve", id)
                        .header("Authorization", bearer(kim))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        act("approve", kim, id, "{}").andExpect(status().isOk());
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        Long id = createApproval(requester, kim);

        mockMvc.perform(get("/api/approvals/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/approvals/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/approvals/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}