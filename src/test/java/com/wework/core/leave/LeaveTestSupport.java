package com.wework.core.leave;

import com.jayway.jsonpath.JsonPath;
import com.wework.core.approval.repository.ApprovalRequestRepository;
import com.wework.core.approval.repository.ApprovalStepRepository;
import com.wework.core.approval.service.ApprovalService;
import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveRequestRepository;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 휴가 신청 통합 테스트의 공통 부모.
 * 날짜는 올해 3월의 첫 월요일 기준으로 계산해서 해가 바뀌어도 같은 요일 구성이 된다 (공휴일 데이터는 넣지 않는다).
 * 회사·사원은 신청자 hong, 결재자 kim·park, 결재선 밖 outsider, 기업관리자 admin, 다른 회사 사원 둘이다.
 */
abstract class LeaveTestSupport extends IntegrationTestSupport {

    @Autowired
    protected LeaveBalanceRepository leaveBalanceRepository;
    @Autowired
    protected LeaveRequestRepository leaveRequestRepository;
    @Autowired
    protected ApprovalRequestRepository approvalRequestRepository;
    @Autowired
    protected ApprovalStepRepository approvalStepRepository;
    @Autowired
    protected ApprovalService approvalService;

    protected static final int YEAR = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    protected static final LocalDate MONDAY = LocalDate.of(YEAR, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    protected static final LocalDate TUESDAY = MONDAY.plusDays(1);
    protected static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    protected static final LocalDate THURSDAY = MONDAY.plusDays(3);
    protected static final LocalDate FRIDAY = MONDAY.plusDays(4);
    protected static final LocalDate SATURDAY = MONDAY.plusDays(5);
    protected static final LocalDate SUNDAY = MONDAY.plusDays(6);
    protected static final LocalDate NEXT_MONDAY = MONDAY.plusDays(7);

    protected Company company;
    protected Department department;
    protected Member admin;
    protected Member hong;       // 신청자
    protected Member kim;        // 결재자 1
    protected Member park;       // 결재자 2
    protected Member outsider;   // 같은 회사지만 결재선에 없는 사원

    protected Company otherCompany;
    protected Member otherEmployee;
    protected Member otherApprover;

    @BeforeEach
    void setUpLeaveBase() {
        company = createCompany("휴가 테스트 회사", "444-44-44444");
        department = createDepartment(company, null, "개발팀");
        admin = createAdmin(company, "admin@leave.test");
        hong = createEmployee(company, department, "hong@leave.test");
        kim = createEmployee(company, department, "kim@leave.test");
        park = createEmployee(company, department, "park@leave.test");
        outsider = createEmployee(company, department, "outsider@leave.test");

        otherCompany = createCompany("다른 휴가 회사", "666-66-66666");
        var otherDepartment = createDepartment(otherCompany, null, "다른 개발팀");
        otherEmployee = createEmployee(otherCompany, otherDepartment, "other@leave.test");
        otherApprover = createEmployee(otherCompany, otherDepartment, "other-approver@leave.test");
    }

    // ===== 데이터 준비 (API가 아니라 리포지토리로 직접 — 실패 원인이 섞이지 않게) =====

    protected void grant(Member member, String days) {
        grant(company, member, days);
    }

    protected void grant(Company target, Member member, String days) {
        leaveBalanceRepository.save(LeaveBalance.create(target, member, YEAR, new BigDecimal(days)));
    }

    protected void rename(Member member, String name) {
        member.update(name, member.getDepartment(), member.getJobGrade(), member.getJobTitle(), member.getHireDate());
    }

    // ===== 신청 API 호출 =====

    protected ResultActions apply(Member actor, String leaveType, LocalDate start, LocalDate end, Member... approvers) throws Exception {
        return applyRaw(actor, body(leaveType, start, end, "가족 행사", approvers));
    }

    protected ResultActions applyRaw(Member actor, String body) throws Exception {
        return mockMvc.perform(post("/api/leave/requests")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 신청이 성공해야 하는 준비용 호출. 만들어진 휴가 신청 번호를 돌려준다. */
    protected Long applyOk(Member actor, String leaveType, LocalDate start, LocalDate end, Member... approvers) throws Exception {
        String json = apply(actor, leaveType, start, end, approvers)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** reason이 null이면 reason 필드를 아예 보내지 않는다. */
    protected static String body(String leaveType, LocalDate start, LocalDate end, String reason, Member... approvers) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < approvers.length; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append(approvers[i].getId());
        }
        String reasonPart = reason == null ? "" : "\"reason\":\"" + reason + "\",";
        return "{\"leaveType\":\"" + leaveType + "\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + reasonPart + "\"approverIds\":[" + ids + "]}";
    }

    protected ResultActions getBalance(Member actor) throws Exception {
        return mockMvc.perform(get("/api/leave/balance")
                .param("year", String.valueOf(YEAR))
                .header("Authorization", bearer(actor)));
    }

    // ===== 확인 도우미 =====

    protected ResultActions expectError(ResultActions result, ResultMatcher expectedStatus, String code) throws Exception {
        return result.andExpect(expectedStatus).andExpect(jsonPath("$.code").value(code));
    }

    protected LeaveRequest leaveOf(Long leaveId) {
        return leaveRequestRepository.findById(leaveId).orElseThrow();
    }

    protected Long approvalIdOf(Long leaveId) {
        return leaveOf(leaveId).getApprovalRequestId();
    }
}