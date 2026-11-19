package com.wework.core.leave;

import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveGrantRule;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaveGrantManualApiTest extends IntegrationTestSupport {

    private static final String URL = "/api/leave/manage/balances/grant";

    // 서비스가 회사 시간대(서울)로 구하는 "올해"와 같은 기준
    private final int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

    @Autowired private LeaveGrantRuleRepository leaveGrantRuleRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;

    private Company companyA;  // 설정: 1년 이상 15일, 3년 이상 16일
    private Company companyB;  // 설정: 1년 이상 20일
    private Company companyC;  // 설정 없음

    private String adminA;
    private String adminB;
    private String adminC;
    private String employeeA;

    private Member m6;   // 6년차 → 16일
    private Member m1;   // 1년차 → 15일
    private Member m0;   // 올해 입사 → 첫해라 제외
    private Member mB;   // 회사 B 사원, 6년차 → 20일
    private Member mC;   // 회사 C 사원 (설정 없음)

    @BeforeEach
    void prepareData() {
        companyA = createCompany("수동지급A", "9960000001");
        companyB = createCompany("수동지급B", "9960000002");
        companyC = createCompany("수동지급C", "9960000003");
        var departmentA = createDepartment(companyA, null, "개발팀");

        // 관리자·로그인용 사원은 입사일이 없어 지급 대상에서 빠지게 한다 (개수 계산이 흔들리지 않도록)
        Member adminMemberA = createAdmin(companyA, "manual-admin-a@test.com");
        Member adminMemberB = createAdmin(companyB, "manual-admin-b@test.com");
        Member adminMemberC = createAdmin(companyC, "manual-admin-c@test.com");
        Member employeeMemberA = createEmployee(companyA, departmentA, "manual-emp-a@test.com");
        for (Member member : new Member[]{adminMemberA, adminMemberB, adminMemberC, employeeMemberA}) {
            ReflectionTestUtils.setField(member, "hireDate", null);
        }
        adminA = bearer(adminMemberA);
        adminB = bearer(adminMemberB);
        adminC = bearer(adminMemberC);
        employeeA = bearer(employeeMemberA);

        saveRule(companyA, 1, "15");
        saveRule(companyA, 3, "16");
        saveRule(companyB, 1, "20");

        m6 = employee(companyA, "manual-6@test.com", LocalDate.of(year - 6, 3, 2));
        m1 = employee(companyA, "manual-1@test.com", LocalDate.of(year - 1, 12, 31));
        m0 = employee(companyA, "manual-0@test.com", LocalDate.of(year, 1, 1));
        mB = employee(companyB, "manual-b@test.com", LocalDate.of(year - 6, 3, 2));
        mC = employee(companyC, "manual-c@test.com", LocalDate.of(year - 6, 3, 2));
    }

    // ---------- 도우미 ----------

    private Member employee(Company company, String email, LocalDate hireDate) {
        Member member = createMember(company, null, email, MemberRole.EMPLOYEE);
        ReflectionTestUtils.setField(member, "hireDate", hireDate);
        return member;
    }

    private void saveRule(Company company, int serviceYear, String days) {
        leaveGrantRuleRepository.save(LeaveGrantRule.create(company, serviceYear, new BigDecimal(days)));
    }

    private ResultActions grant(String auth, int grantYear) throws Exception {
        return mockMvc.perform(post(URL)
                .header("Authorization", auth)
                .param("year", String.valueOf(grantYear)));
    }

    // 장부가 없으면 null
    private LeaveBalance findBalance(Company company, Member member, int balanceYear) {
        return leaveBalanceRepository
                .findByCompanyIdAndMemberIdAndYear(company.getId(), member.getId(), balanceYear)
                .orElse(null);
    }

    private void assertGranted(String expectedDays, LeaveBalance balance) {
        assertNotNull(balance, "장부가 있어야 한다");
        assertEquals(0, new BigDecimal(expectedDays).compareTo(balance.getGrantedDays()));
    }

    // ================= 지급 =================

    @Test
    @DisplayName("설정 표대로 지급하고, 입사 첫해 사원은 제외한다")
    void grantsByRules() throws Exception {
        grant(adminA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year))
                .andExpect(jsonPath("$.grantedCount").value(2));

        assertGranted("16", findBalance(companyA, m6, year));
        assertGranted("15", findBalance(companyA, m1, year));
        assertNull(findBalance(companyA, m0, year));
    }

    @Test
    @DisplayName("이미 장부가 있는 사원은 건드리지 않는다 (관리자가 직접 입력한 값 보호)")
    void doesNotOverwriteExisting() throws Exception {
        leaveBalanceRepository.save(LeaveBalance.create(companyA, m6, year, new BigDecimal("20")));

        grant(adminA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(1));

        assertGranted("20", findBalance(companyA, m6, year));
        assertGranted("15", findBalance(companyA, m1, year));
    }

    @Test
    @DisplayName("두 번 호출하면 두 번째는 0명이고 값은 그대로다 (멱등)")
    void secondCallGrantsNobody() throws Exception {
        grant(adminA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(2));

        grant(adminA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(0));

        assertGranted("16", findBalance(companyA, m6, year));
        assertGranted("15", findBalance(companyA, m1, year));
    }

    @Test
    @DisplayName("설정 표가 비어 있으면 409 LEAVE_GRANT_RULES_NOT_SET이고 아무것도 저장하지 않는다")
    void emptyRulesConflict() throws Exception {
        grant(adminC, year)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAVE_GRANT_RULES_NOT_SET"));

        assertNull(findBalance(companyC, mC, year));
    }

    // ================= 연도 =================

    @Test
    @DisplayName("연도 하한: 2014는 400, 2015는 통과 (쌍)")
    void yearLowerBoundary() throws Exception {
        grant(adminA, 2014)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_YEAR"));

        grant(adminA, 2015)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2015));
    }

    @Test
    @DisplayName("연도 상한: 올해+2는 400, 올해+1은 통과 (쌍)")
    void yearUpperBoundary() throws Exception {
        grant(adminA, year + 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_YEAR"));

        grant(adminA, year + 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year + 1));
    }

    @Test
    @DisplayName("연도 검사가 설정 표 검사보다 먼저다 (설정이 없는 회사도 잘못된 연도는 400)")
    void yearCheckedBeforeRules() throws Exception {
        grant(adminC, 2014)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_YEAR"));
    }

    @Test
    @DisplayName("year를 보내지 않으면 400")
    void yearMissing() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", adminA))
                .andExpect(status().isBadRequest());
    }

    // ================= 권한 =================

    @Test
    @DisplayName("일반 사원은 403이고 장부가 생기지 않는다")
    void employeeForbidden() throws Exception {
        grant(employeeA, year)
                .andExpect(status().isForbidden());

        assertNull(findBalance(companyA, m6, year));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void noTokenUnauthorized() throws Exception {
        mockMvc.perform(post(URL).param("year", String.valueOf(year)))
                .andExpect(status().isUnauthorized());
    }

    // ================= 회사 격리 =================

    @Test
    @DisplayName("회사마다 자기 설정 표와 자기 사원만 쓴다")
    void companyIsolation() throws Exception {
        grant(adminA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(2));

        // A를 돌렸다고 B의 사원에게 장부가 생기면 안 된다
        assertNull(findBalance(companyB, mB, year));

        grant(adminB, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(1));

        // B 사원은 B의 설정(20일)을 받고, A 사원은 A의 설정 그대로다
        assertGranted("20", findBalance(companyB, mB, year));
        assertGranted("16", findBalance(companyA, m6, year));
    }
}