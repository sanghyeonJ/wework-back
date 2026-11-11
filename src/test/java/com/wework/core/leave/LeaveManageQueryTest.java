package com.wework.core.leave;

import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.member.entity.Member;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 휴가 신청 조회(GET /api/leave/manage/requests) 테스트.
 * 데이터는 API가 아니라 리포지토리로 직접 만든다 (날짜·상태를 마음대로 정하려고).
 */
class LeaveManageQueryTest extends LeaveTestSupport {

    // ===== 회사 전체 =====

    @Test
    void 회사_전체_신청이_나오고_사원_이름과_부서_이름이_함께_나온다() throws Exception {
        rename(hong, "홍길동");
        LeaveRequest hongLeave = saveLeave(hong, LeaveType.ANNUAL, MONDAY, TUESDAY, "2", LeaveRequestStatus.PENDING);
        hongLeave.linkApproval(77L);
        saveLeave(kim, LeaveType.HALF_AM, WEDNESDAY, WEDNESDAY, "0.5", LeaveRequestStatus.APPROVED);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].leaveType").value("HALF_AM"))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.content[1].id").value(hongLeave.getId().intValue()))
                .andExpect(jsonPath("$.content[1].memberId").value(hong.getId().intValue()))
                .andExpect(jsonPath("$.content[1].memberName").value("홍길동"))
                .andExpect(jsonPath("$.content[1].departmentName").value("개발팀"))
                .andExpect(jsonPath("$.content[1].leaveType").value("ANNUAL"))
                .andExpect(jsonPath("$.content[1].startDate").value(MONDAY.toString()))
                .andExpect(jsonPath("$.content[1].endDate").value(TUESDAY.toString()))
                .andExpect(jsonPath("$.content[1].days").value(2.0))
                .andExpect(jsonPath("$.content[1].status").value("PENDING"))
                .andExpect(jsonPath("$.content[1].approvalRequestId").value(77))
                .andExpect(jsonPath("$.content[1].reason").doesNotExist());
    }

    @Test
    void 목록은_시작일_최신순이고_같은_날이면_번호가_큰_것이_먼저다() throws Exception {
        LeaveRequest monday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest fridayKim = saveLeave(kim, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest fridayPark = saveLeave(park, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                        fridayPark.getId().intValue(), fridayKim.getId().intValue(), monday.getId().intValue())));
    }

    // ===== 회사 격리 =====

    @Test
    void 다른_회사의_신청은_섞이지_않는다() throws Exception {
        LeaveRequest mine = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        saveLeave(otherCompany, otherEmployee, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine.getId().intValue()));
    }

    // ===== 사원 필터 =====

    @Test
    void 사원을_고르면_그_사원_것만_나오고_다른_회사_사원과_없는_번호는_빈_목록이다() throws Exception {
        LeaveRequest hongMonday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest hongWednesday = saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.APPROVED);
        saveLeave(kim, LeaveType.ANNUAL, TUESDAY, TUESDAY, "1", LeaveRequestStatus.PENDING);
        saveLeave(otherCompany, otherEmployee, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR).param("memberId", String.valueOf(hong.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                        hongWednesday.getId().intValue(), hongMonday.getId().intValue())));

        // 다른 회사 사원의 번호를 넣어도 내 회사 조건에 막혀서 빈 목록 (404도 아니다)
        mockMvc.perform(manageRequests(admin, YEAR).param("memberId", String.valueOf(otherEmployee.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(manageRequests(admin, YEAR).param("memberId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ===== 상태 필터 =====

    @Test
    void 상태를_고르면_그_상태만_나오고_안_고르면_전체가_나온다() throws Exception {
        saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        saveLeave(hong, LeaveType.ANNUAL, TUESDAY, TUESDAY, "1", LeaveRequestStatus.APPROVED);
        saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.REJECTED);
        saveLeave(hong, LeaveType.ANNUAL, THURSDAY, THURSDAY, "1", LeaveRequestStatus.CANCELED);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));

        for (LeaveRequestStatus one : LeaveRequestStatus.values()) {
            mockMvc.perform(manageRequests(admin, YEAR).param("status", one.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].status").value(one.name()));
        }
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

    private MockHttpServletRequestBuilder manageRequests(Member actor, int year) {
        return get("/api/leave/manage/requests")
                .param("year", String.valueOf(year))
                .header("Authorization", bearer(actor));
    }
}