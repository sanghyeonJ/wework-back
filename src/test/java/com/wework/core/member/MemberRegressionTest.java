package com.wework.core.member;

import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.member.entity.Member;
import com.wework.core.position.entity.JobGrade;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("사원 회귀 테스트 (고친 버그 재발 방지)")
class MemberRegressionTest extends IntegrationTestSupport {

    // createCompany()가 만드는 회사 시간대와 같게
    private static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Seoul");

    private Member admin;
    private Member leader;
    private Member hqMember;
    private Member teamMember;
    private Department devHq;
    private Department devTeam;
    private JobGrade grade;

    @BeforeEach
    void setUp() {
        Company company = createCompany("위워크 서울", "111");

        // 본사 > 개발본부 > 개발팀
        Department root = createDepartment(company, null, "본사");
        devHq = createDepartment(company, root, "개발본부");
        devTeam = createDepartment(company, devHq, "개발팀");
        grade = createJobGrade(company, "과장", 20);

        admin = createAdmin(company, "admin@test.com");
        leader = createEmployee(company, devTeam, "leader@test.com");
        hqMember = createEmployee(company, devHq, "hq-member@test.com");
        teamMember = createEmployee(company, devTeam, "team-member@test.com");

        devTeam.changeLeader(leader);
    }

    // ===== 부서장 수정 규칙 (버그: 부서 변경 판단 조건이 반대였음) =====

    @Test
    @DisplayName("부서장도 부서를 그대로 두면 이름을 수정할 수 있다")
    void leaderCanUpdateNameWithSameDepartment() throws Exception {
        asAdmin(patch("/api/company/members/{id}", leader.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "새이름", "departmentId": %d}
                        """.formatted(devTeam.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새이름"))
                .andExpect(jsonPath("$.leader").value(true));
    }

    @Test
    @DisplayName("부서장의 부서를 바꾸면 409")
    void leaderCannotChangeDepartment() throws Exception {
        asAdmin(patch("/api/company/members/{id}", leader.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "departmentId": %d}
                        """.formatted(leader.getName(), devHq.getId())))
                .andExpect(status().isConflict())
                .andExpect(errorCode("LEADER_CANNOT_CHANGE_DEPARTMENT"));
    }

    // ===== 목록 페이징 (버그: 개수 쿼리가 count가 아니라 회원을 조회했음) =====

    @Test
    @DisplayName("페이지 크기보다 사원이 많아도 전체 개수가 정확하다")
    void pagingTotalCount() throws Exception {
        // 사원 4명(admin, leader, hqMember, teamMember) → size=1이면 개수 쿼리가 실행됨
        asAdmin(get("/api/company/members").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(4));
    }

    // ===== 직급만 있는 사원 (버그: 직책 id를 꺼낼 때 직급으로 null 검사 → NPE) =====

    @Test
    @DisplayName("직책 없이 직급만 있는 사원도 조회된다")
    void memberWithGradeOnly() throws Exception {
        teamMember.update(teamMember.getName(), devTeam, grade, null, null);

        asAdmin(get("/api/company/members/{id}", teamMember.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobGradeId").value(grade.getId()))
                .andExpect(jsonPath("$.jobTitleId").value(nullValue()));

        asAdmin(get("/api/company/members"))
                .andExpect(status().isOk());
    }

    // ===== 하위 부서 포함 검색 =====

    @Test
    @DisplayName("하위 부서 포함이면 하위 부서 사원까지, 아니면 직속만")
    void includeSubDepartments() throws Exception {
        // 개발본부 직속: hqMember
        asAdmin(get("/api/company/members").param("departmentId", devHq.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        // 개발본부 + 개발팀: hqMember, leader, teamMember
        asAdmin(get("/api/company/members")
                .param("departmentId", devHq.getId().toString())
                .param("includeSubDepartments", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    // ===== 검색어 (버그: 검색어를 %로 감싸지 않고 그대로 넘겨 완전 일치만 찾았음) =====

    @Test
    @DisplayName("이름의 일부만 입력해도 검색된다")
    void searchByPartOfName() throws Exception {
        // 이름을 직접 정해 상황 준비 (같은 트랜잭션, 조회 전 자동 flush)
        teamMember.update("김철수", devTeam, null, null, null);

        // 이름이 "김철수"인 사원을 "철수"로 검색 → 완전 일치만 찾던 때는 0건이었음
        asAdmin(get("/api/company/members").param("keyword", "철수"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(teamMember.getId()));

        // 확인용: 이름에 없는 글자는 0건 (조건이 아무나 통과시키는 것이 아님)
        asAdmin(get("/api/company/members").param("keyword", "박영희"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("이메일은 대소문자를 구분하지 않고 일부만 입력해도 검색된다")
    void searchByEmailIgnoringCase() throws Exception {
        // 이메일은 team-member@test.com (소문자로 저장됨) → 대문자 일부로 검색
        asAdmin(get("/api/company/members").param("keyword", "TEAM-MEMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(teamMember.getId()));
    }

    // ===== 퇴사일 =====

    @Test
    @DisplayName("퇴사일이 없으면 400")
    void resignWithoutDate() throws Exception {
        asAdmin(post("/api/company/members/{id}/resign", hqMember.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_INPUT"));
    }

    @Test
    @DisplayName("퇴사일이 미래면 400")
    void resignWithFutureDate() throws Exception {
        LocalDate tomorrow = LocalDate.now(COMPANY_ZONE).plusDays(1);

        resign(hqMember, tomorrow)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_RESIGN_DATE"));
    }

    @Test
    @DisplayName("퇴사일이 입사일보다 이전이면 400")
    void resignBeforeHireDate() throws Exception {
        LocalDate hireDate = LocalDate.now(COMPANY_ZONE).minusDays(10);
        hqMember.update(hqMember.getName(), devHq, null, null, hireDate);

        resign(hqMember, hireDate.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_RESIGN_DATE"));
    }

    @Test
    @DisplayName("입사일 ~ 오늘 사이면 퇴사 처리되고 퇴사일이 저장된다")
    void resignSuccess() throws Exception {
        LocalDate hireDate = LocalDate.now(COMPANY_ZONE).minusDays(10);
        LocalDate resignDate = LocalDate.now(COMPANY_ZONE).minusDays(1);
        hqMember.update(hqMember.getName(), devHq, null, null, hireDate);

        resign(hqMember, resignDate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESIGNED"))
                .andExpect(jsonPath("$.resignDate").value(resignDate.toString()));
    }

    @Test
    @DisplayName("부서장은 퇴사 처리할 수 없다")
    void leaderCannotResign() throws Exception {
        resign(leader, LocalDate.now(COMPANY_ZONE))
                .andExpect(status().isConflict())
                .andExpect(errorCode("LEADER_CANNOT_RESIGN"));
    }

    // ===== 도우미 =====

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(admin)));
    }

    private ResultActions resign(Member member, LocalDate resignDate) throws Exception {
        return asAdmin(post("/api/company/members/{id}/resign", member.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resignDate": "%s"}
                        """.formatted(resignDate)));
    }

    private static ResultMatcher errorCode(String code) {
        return jsonPath("$.code").value(code);
    }
}