package com.wework.core.leave;

import com.jayway.jsonpath.JsonPath;
import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.member.entity.Member;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 휴가 신청 목록, 휴가 신청 상세(권한), 승인 대기 일수 테스트.
 * 목록·대기 일수의 데이터는 API가 아니라 리포지토리로 직접 만든다 (날짜·상태를 마음대로 정하려고).
 * 상세는 결재선이 있어야 권한을 확인할 수 있어서 신청 API로 만든다.
 */
class LeaveQueryTest extends LeaveTestSupport {

    // ===== 내 신청 목록 =====

    @Test
    void 목록은_시작일_최신순이고_같은_날이면_번호가_큰_것이_먼저이며_작년과_남의_신청은_빠진다() throws Exception {
        LeaveRequest first = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest second = saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.APPROVED);
        LeaveRequest third = saveLeave(hong, LeaveType.HALF_AM, FRIDAY, FRIDAY, "0.5", LeaveRequestStatus.PENDING);
        LocalDate lastYear = LocalDate.of(YEAR - 1, 12, 20);
        saveLeave(hong, LeaveType.ANNUAL, lastYear, lastYear, "1", LeaveRequestStatus.PENDING);   // 작년
        saveLeave(kim, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);        // 다른 사원
        saveLeave(otherCompany, otherEmployee, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);   // 다른 회사

        mockMvc.perform(myRequests(hong, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                        third.getId().intValue(), second.getId().intValue(), first.getId().intValue())));
    }

    @Test
    void 목록_항목에는_결재_번호가_있고_사유는_없다() throws Exception {
        LeaveRequest leave = saveLeave(hong, LeaveType.ANNUAL, MONDAY, TUESDAY, "2", LeaveRequestStatus.PENDING);
        leave.linkApproval(77L);

        mockMvc.perform(myRequests(hong, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(leave.getId().intValue()))
                .andExpect(jsonPath("$.content[0].leaveType").value("ANNUAL"))
                .andExpect(jsonPath("$.content[0].startDate").value(MONDAY.toString()))
                .andExpect(jsonPath("$.content[0].endDate").value(TUESDAY.toString()))
                .andExpect(jsonPath("$.content[0].days").value(2.0))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].approvalRequestId").value(77))
                .andExpect(jsonPath("$.content[0].reason").doesNotExist());
    }

    @Test
    void 상태를_고르면_그_상태만_나오고_안_고르면_전체가_나온다() throws Exception {
        saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        saveLeave(hong, LeaveType.ANNUAL, TUESDAY, TUESDAY, "1", LeaveRequestStatus.APPROVED);
        saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.REJECTED);
        saveLeave(hong, LeaveType.ANNUAL, THURSDAY, THURSDAY, "1", LeaveRequestStatus.CANCELED);

        mockMvc.perform(myRequests(hong, YEAR))
                .andExpect(jsonPath("$.page.totalElements").value(4));

        for (LeaveRequestStatus one : LeaveRequestStatus.values()) {
            mockMvc.perform(myRequests(hong, YEAR).param("status", one.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].status").value(one.name()));
        }
    }

    @Test
    void 목록_페이징_size가_1이면_한_건씩_나오고_범위를_넘은_페이지는_빈_목록이다() throws Exception {
        LeaveRequest monday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest wednesday = saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest friday = saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(myRequests(hong, YEAR).param("size", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(friday.getId().intValue()))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        mockMvc.perform(myRequests(hong, YEAR).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(wednesday.getId().intValue()));

        mockMvc.perform(myRequests(hong, YEAR).param("size", "1").param("page", "2"))
                .andExpect(jsonPath("$.content[0].id").value(monday.getId().intValue()));

        mockMvc.perform(myRequests(hong, YEAR).param("size", "1").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void 목록_size를_101로_요청해도_최대_100건만_나온다() throws Exception {
        for (int i = 0; i < 101; i++) {
            saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        }

        mockMvc.perform(myRequests(hong, YEAR).param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void 목록은_sort를_보내도_무시하고_시작일_최신순을_유지한다() throws Exception {
        LeaveRequest monday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest wednesday = saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest friday = saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);

        for (String sort : new String[]{"startDate,asc", "id,asc"}) {
            mockMvc.perform(myRequests(hong, YEAR).param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                            friday.getId().intValue(), wednesday.getId().intValue(), monday.getId().intValue())));
        }
    }

    @Test
    void 목록은_사원마다_자기_신청만_나온다_다른_회사_사원도_섞이지_않는다() throws Exception {
        LeaveRequest hongLeave = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest kimLeave = saveLeave(kim, LeaveType.ANNUAL, TUESDAY, TUESDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest otherLeave = saveLeave(otherCompany, otherEmployee, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(myRequests(hong, YEAR))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(hongLeave.getId().intValue()));
        mockMvc.perform(myRequests(kim, YEAR))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kimLeave.getId().intValue()));
        mockMvc.perform(myRequests(otherEmployee, YEAR))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(otherLeave.getId().intValue()));
    }

    @Test
    void 목록_연도_경계_2015와_내년은_성공하고_2014와_내후년은_400이다() throws Exception {
        mockMvc.perform(myRequests(hong, 2015))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(myRequests(hong, YEAR + 1))
                .andExpect(status().isOk());

        expectError(mockMvc.perform(myRequests(hong, 2014)), status().isBadRequest(), "INVALID_LEAVE_YEAR");
        expectError(mockMvc.perform(myRequests(hong, YEAR + 2)), status().isBadRequest(), "INVALID_LEAVE_YEAR");
    }

    @Test
    void 목록은_year가_없거나_상태가_틀리면_400이고_토큰이_없으면_401이며_기업관리자도_볼_수_있다() throws Exception {
        mockMvc.perform(get("/api/leave/requests/me").header("Authorization", bearer(hong)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(myRequests(hong, YEAR).param("status", "ABC"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leave/requests/me").param("year", String.valueOf(YEAR)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(myRequests(admin, YEAR)).andExpect(status().isOk());
    }

    // ===== 신청 상세 =====

    @Test
    void 신청자_결재자_어느_단계든_기업관리자는_상세를_볼_수_있다() throws Exception {
        rename(hong, "홍길동");
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, TUESDAY, kim, park);
        Long approvalId = approvalIdOf(id);

        for (Member viewer : new Member[]{hong, kim, park, admin}) {   // park는 아직 차례가 아닌 2단계 결재자
            detail(viewer, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.intValue()))
                    .andExpect(jsonPath("$.memberName").value("홍길동"))
                    .andExpect(jsonPath("$.leaveType").value("ANNUAL"))
                    .andExpect(jsonPath("$.startDate").value(MONDAY.toString()))
                    .andExpect(jsonPath("$.endDate").value(TUESDAY.toString()))
                    .andExpect(jsonPath("$.days").value(2.0))
                    .andExpect(jsonPath("$.reason").value("가족 행사"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.approvalRequestId").value(approvalId.intValue()));
        }
    }

    @Test
    void 결재선_밖_사원과_다른_회사_사원과_없는_번호는_모두_같은_404이다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, MONDAY, kim);

        for (Member blocked : new Member[]{outsider, otherEmployee}) {
            expectNotFound(detail(blocked, id));
        }
        expectNotFound(detail(hong, 999999L));
    }

    @Test
    void 결재_번호가_없는_신청은_본인과_기업관리자에게도_보이지_않는다() throws Exception {
        LeaveRequest orphan = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);

        expectNotFound(detail(hong, orphan.getId()));
        expectNotFound(detail(admin, orphan.getId()));
    }

    @Test
    void 사유_없이_낸_신청의_상세는_사유가_null이다() throws Exception {
        grant(hong, "15");
        String json = applyRaw(hong, body("ANNUAL", MONDAY, MONDAY, null, kim))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(json, "$.id")).longValue();

        detail(hong, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value(Matchers.nullValue()));
    }

    @Test
    void 승인된_뒤에도_상세는_보이고_상태가_바뀐다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, MONDAY, kim);

        approvalService.approve(company.getId(), kim.getId(), approvalIdOf(id), null);

        for (Member viewer : new Member[]{hong, kim, admin}) {
            detail(viewer, id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }

    @Test
    void 상세는_토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/leave/requests/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    // ===== 승인 대기 일수 =====

    @Test
    void 대기_일수는_연차와_반차의_대기_건만_더하고_병가_승인_반려_취소_작년_다른_사원은_뺀다() throws Exception {
        grant(hong, "15");
        saveLeave(hong, LeaveType.ANNUAL, MONDAY, TUESDAY, "2", LeaveRequestStatus.PENDING);                 // 더함
        saveLeave(hong, LeaveType.HALF_AM, WEDNESDAY, WEDNESDAY, "0.5", LeaveRequestStatus.PENDING);        // 더함
        saveLeave(hong, LeaveType.SICK, THURSDAY, THURSDAY, "1", LeaveRequestStatus.PENDING);               // 병가는 뺌
        saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.APPROVED);                // 승인은 뺌
        saveLeave(hong, LeaveType.ANNUAL, NEXT_MONDAY, NEXT_MONDAY, "1", LeaveRequestStatus.REJECTED);      // 반려는 뺌
        saveLeave(hong, LeaveType.ANNUAL, NEXT_MONDAY.plusDays(1), NEXT_MONDAY.plusDays(1), "1", LeaveRequestStatus.CANCELED);   // 취소는 뺌
        LocalDate lastYear = LocalDate.of(YEAR - 1, 12, 20);
        saveLeave(hong, LeaveType.ANNUAL, lastYear, lastYear, "1", LeaveRequestStatus.PENDING);             // 작년은 뺌
        saveLeave(kim, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);                  // 다른 사원은 뺌

        getBalance(hong)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDays").value(0.0))
                .andExpect(jsonPath("$.remainingDays").value(15.0))
                .andExpect(jsonPath("$.pendingDays").value(2.5));
    }

    @Test
    void 장부가_없으면_대기_일수도_0이다() throws Exception {
        saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);

        getBalance(hong)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(0.0))
                .andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    @Test
    void 관리자_목록의_대기_일수는_사원마다_다르고_장부가_없는_사원은_balance가_null이다() throws Exception {
        rename(hong, "Alice");
        rename(kim, "Bob");
        rename(park, "Carol");
        rename(outsider, "Dan");
        rename(admin, "Zed");
        grant(hong, "15");
        grant(kim, "15");
        saveLeave(hong, LeaveType.ANNUAL, MONDAY, TUESDAY, "2", LeaveRequestStatus.PENDING);
        saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.APPROVED);    // 승인은 대기가 아님
        saveLeave(kim, LeaveType.HALF_AM, MONDAY, MONDAY, "0.5", LeaveRequestStatus.PENDING);
        saveLeave(park, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);     // 장부가 없는 사원

        mockMvc.perform(get("/api/leave/manage/balances")
                        .param("year", String.valueOf(YEAR))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].memberName").value("Alice"))
                .andExpect(jsonPath("$.content[0].balance.pendingDays").value(2.0))
                .andExpect(jsonPath("$.content[1].memberName").value("Bob"))
                .andExpect(jsonPath("$.content[1].balance.pendingDays").value(0.5))
                .andExpect(jsonPath("$.content[2].memberName").value("Carol"))
                .andExpect(jsonPath("$.content[2].balance").value(Matchers.nullValue()));
    }

    // ===== 도우미 =====

    private LeaveRequest saveLeave(Member member, LeaveType type, LocalDate start, LocalDate end,
                                   String days, LeaveRequestStatus status) {
        return saveLeave(company, member, type, start, end, days, status);
    }

    private LeaveRequest saveLeave(Company target, Member member, LeaveType type, LocalDate start, LocalDate end,
                                   String days, LeaveRequestStatus leaveStatus) {
        LeaveRequest leave = leaveRequestRepository.save(
                LeaveRequest.create(target, member, type, start, end, new BigDecimal(days), "사유"));
        if (leaveStatus == LeaveRequestStatus.APPROVED) {
            leave.approve();
        } else if (leaveStatus == LeaveRequestStatus.REJECTED) {
            leave.reject();
        } else if (leaveStatus == LeaveRequestStatus.CANCELED) {
            leave.cancel();
        }
        return leave;
    }

    private MockHttpServletRequestBuilder myRequests(Member actor, int year) {
        return get("/api/leave/requests/me")
                .param("year", String.valueOf(year))
                .header("Authorization", bearer(actor));
    }

    private ResultActions detail(Member actor, Long id) throws Exception {
        return mockMvc.perform(get("/api/leave/requests/{id}", id)
                .header("Authorization", bearer(actor)));
    }

    /** 없는 번호든 못 보는 번호든 코드와 문구가 똑같아야 존재 여부가 드러나지 않는다. */
    private void expectNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAVE_REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("휴가 신청을 찾을 수 없습니다."));
    }
}