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
import java.time.ZoneId;

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

    // ===== 새로 추가 =====

    @Test
    void 연도는_시작일_기준이라_12월_31일과_1월_1일이_서로_다른_해에_나온다() throws Exception {
        LocalDate lastYearEnd = LocalDate.of(YEAR - 1, 12, 31);
        LocalDate thisYearStart = LocalDate.of(YEAR, 1, 1);
        LocalDate thisYearEnd = LocalDate.of(YEAR, 12, 31);
        LocalDate nextYearStart = LocalDate.of(YEAR + 1, 1, 1);
        LeaveRequest last = saveLeave(hong, LeaveType.ANNUAL, lastYearEnd, lastYearEnd, "1", LeaveRequestStatus.PENDING);
        LeaveRequest start = saveLeave(hong, LeaveType.ANNUAL, thisYearStart, thisYearStart, "1", LeaveRequestStatus.PENDING);
        LeaveRequest end = saveLeave(hong, LeaveType.ANNUAL, thisYearEnd, thisYearEnd, "1", LeaveRequestStatus.PENDING);
        LeaveRequest next = saveLeave(hong, LeaveType.ANNUAL, nextYearStart, nextYearStart, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                        end.getId().intValue(), start.getId().intValue())));

        mockMvc.perform(manageRequests(admin, YEAR - 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(last.getId().intValue()));

        mockMvc.perform(manageRequests(admin, YEAR + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(next.getId().intValue()));
    }

    @Test
    void 퇴사한_사원의_신청도_나온다() throws Exception {
        rename(park, "퇴사자");
        LeaveRequest leave = saveLeave(park, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.APPROVED);
        park.resign(LocalDate.now(ZoneId.of("Asia/Seoul")));

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(leave.getId().intValue()))
                .andExpect(jsonPath("$.content[0].memberName").value("퇴사자"));
    }

    @Test
    void 부서가_없는_사원의_신청도_나오고_부서_이름은_null이다() throws Exception {
        // admin은 createAdmin으로 만들어져 부서가 없다
        LeaveRequest hongLeave = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest adminLeave = saveLeave(admin, LeaveType.ANNUAL, TUESDAY, TUESDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(adminLeave.getId().intValue()))
                .andExpect(jsonPath("$.content[0].departmentName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.content[1].id").value(hongLeave.getId().intValue()))
                .andExpect(jsonPath("$.content[1].departmentName").value("개발팀"));
    }

    @Test
    void 페이징_size가_1이면_한_건씩_나오고_범위를_넘은_페이지는_빈_목록이다() throws Exception {
        LeaveRequest monday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest wednesday = saveLeave(hong, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest friday = saveLeave(hong, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);

        mockMvc.perform(manageRequests(admin, YEAR).param("size", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(friday.getId().intValue()))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));

        mockMvc.perform(manageRequests(admin, YEAR).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(wednesday.getId().intValue()));

        mockMvc.perform(manageRequests(admin, YEAR).param("size", "1").param("page", "2"))
                .andExpect(jsonPath("$.content[0].id").value(monday.getId().intValue()));

        mockMvc.perform(manageRequests(admin, YEAR).param("size", "1").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void size를_101로_요청해도_최대_100건만_나온다() throws Exception {
        for (int i = 0; i < 101; i++) {
            saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        }

        mockMvc.perform(manageRequests(admin, YEAR).param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void sort를_보내도_무시하고_시작일_최신순을_유지한다() throws Exception {
        // 사원 이름 순(hong, kim, park)이 날짜 순(월, 수, 금)과 같아서, 최신순의 정반대가 되게 짬
        LeaveRequest monday = saveLeave(hong, LeaveType.ANNUAL, MONDAY, MONDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest wednesday = saveLeave(kim, LeaveType.ANNUAL, WEDNESDAY, WEDNESDAY, "1", LeaveRequestStatus.PENDING);
        LeaveRequest friday = saveLeave(park, LeaveType.ANNUAL, FRIDAY, FRIDAY, "1", LeaveRequestStatus.PENDING);

        for (String sort : new String[]{"startDate,asc", "id,asc", "member.name,asc"}) {
            mockMvc.perform(manageRequests(admin, YEAR).param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id").value(Matchers.contains(
                            friday.getId().intValue(), wednesday.getId().intValue(), monday.getId().intValue())));
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