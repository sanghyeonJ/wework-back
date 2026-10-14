package com.wework.core;

import com.wework.core.company.Company;
import com.wework.core.department.Department;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRole;
import com.wework.core.position.JobGrade;
import com.wework.core.position.JobTitle;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("회사 데이터 격리")
class CompanyIsolationTest extends IntegrationTestSupport {

    private Member seoulAdmin;
    private Member tokyoAdmin;
    private Member seoulEmployee;
    private Department seoulDept;
    private Department tokyoDept;
    private JobGrade seoulGrade;
    private JobTitle seoulTitle;

    @BeforeEach
    void setUp() {
        Company seoul = createCompany("위워크 서울", "111");
        Company tokyo = createCompany("위워크 도쿄", "222");

        seoulAdmin = createAdmin(seoul, "seoul-admin@test.com");
        tokyoAdmin = createAdmin(tokyo, "tokyo-admin@test.com");

        seoulDept = createDepartment(seoul, null, "개발팀");
        tokyoDept = createDepartment(tokyo, null, "본사");
        seoulGrade = createJobGrade(seoul, "사원", 10);
        seoulTitle = createJobTitle(seoul, "팀원", 10);

        // 서울 사원: 서울 부서·직급·직책을 "사용 중"으로 만듦
        seoulEmployee = createEmployee(seoul, seoulDept, "seoul-employee@test.com");
        seoulEmployee.update(seoulEmployee.getName(), seoulDept, seoulGrade, seoulTitle, null);
    }

    // ===== 부서 =====

    @Test
    @DisplayName("부서 목록에 다른 회사 부서가 안 보인다")
    void departmentList() throws Exception {
        asTokyo(get("/api/company/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tokyoDept.getId()));
    }

    @Test
    @DisplayName("다른 회사 부서 수정 → 404")
    void departmentUpdate() throws Exception {
        asTokyo(patch("/api/company/departments/{id}", seoulDept.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "바꾼이름", "parentId": null}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 부서장 지정 → 404")
    void departmentLeader() throws Exception {
        asTokyo(patch("/api/company/departments/{id}/leader", seoulDept.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"leaderId": null}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 부서 삭제 → 404 (사용 중 409가 먼저 나오면 안 됨)")
    void departmentDelete() throws Exception {
        asTokyo(delete("/api/company/departments/{id}", seoulDept.getId()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 부서를 상위 부서로 지정 → 404")
    void departmentCreateWithOtherParent() throws Exception {
        asTokyo(post("/api/company/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "영업팀", "parentId": %d}
                        """.formatted(seoulDept.getId())))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 사원을 부서장으로 지정 → 거절")
    void departmentLeaderFromOtherCompany() throws Exception {
        asTokyo(patch("/api/company/departments/{id}/leader", tokyoDept.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"leaderId": %d}
                        """.formatted(seoulEmployee.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_DEPARTMENT_LEADER"));
    }

    @Test
    @DisplayName("다른 회사와 같은 부서 이름은 사용 가능")
    void departmentSameNameAllowed() throws Exception {
        asTokyo(post("/api/company/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "개발팀", "parentId": %d}
                        """.formatted(tokyoDept.getId())))
                .andExpect(status().isCreated());
    }

    // ===== 직급·직책 =====

    @Test
    @DisplayName("직급·직책 목록에 다른 회사 것이 안 보인다")
    void positionList() throws Exception {
        asTokyo(get("/api/company/job-grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        asTokyo(get("/api/company/job-titles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("다른 회사 직급 수정·삭제 → 404 (사용 중 409가 먼저 나오면 안 됨)")
    void jobGradeUpdateAndDelete() throws Exception {
        asTokyo(patch("/api/company/job-grades/{id}", seoulGrade.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "바꾼이름", "level": 10}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_GRADE_NOT_FOUND"));

        asTokyo(delete("/api/company/job-grades/{id}", seoulGrade.getId()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_GRADE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 직책 수정·삭제 → 404 (사용 중 409가 먼저 나오면 안 됨)")
    void jobTitleUpdateAndDelete() throws Exception {
        asTokyo(patch("/api/company/job-titles/{id}", seoulTitle.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "바꾼이름", "level": 10}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_TITLE_NOT_FOUND"));

        asTokyo(delete("/api/company/job-titles/{id}", seoulTitle.getId()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_TITLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사와 같은 직급 이름은 사용 가능")
    void jobGradeSameNameAllowed() throws Exception {
        asTokyo(post("/api/company/job-grades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "사원", "level": 10}
                        """))
                .andExpect(status().isCreated());
    }

    // ===== 사원 =====

    @Test
    @DisplayName("사원 목록·검색에 다른 회사 사원이 안 보인다")
    void memberList() throws Exception {
        asTokyo(get("/api/company/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(tokyoAdmin.getId()));

        asTokyo(get("/api/company/members").param("keyword", "seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        asTokyo(get("/api/company/members").param("departmentId", seoulDept.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("다른 회사 사원 상세·수정·권한·퇴사·초대 재발급 → 404")
    void memberAccess() throws Exception {
        Long id = seoulEmployee.getId();

        asTokyo(get("/api/company/members/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        asTokyo(patch("/api/company/members/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "바꾼이름"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        asTokyo(patch("/api/company/members/{id}/role", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role": "COMPANY_ADMIN"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        asTokyo(post("/api/company/members/{id}/resign", id))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        asTokyo(post("/api/company/members/{id}/invitation", id))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("사원 발급 시 다른 회사 부서·직급·직책 지정 → 404")
    void memberCreateWithOtherCompanyData() throws Exception {
        asTokyo(post("/api/company/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "new1@test.com", "name": "신입", "departmentId": %d}
                        """.formatted(seoulDept.getId())))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));

        asTokyo(post("/api/company/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "new2@test.com", "name": "신입", "jobGradeId": %d}
                        """.formatted(seoulGrade.getId())))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_GRADE_NOT_FOUND"));

        asTokyo(post("/api/company/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "new3@test.com", "name": "신입", "jobTitleId": %d}
                        """.formatted(seoulTitle.getId())))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("JOB_TITLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("이메일은 전체 시스템에서 유일 → 다른 회사 이메일로 발급 시 409")
    void emailIsGloballyUnique() throws Exception {
        asTokyo(post("/api/company/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "SEOUL-EMPLOYEE@test.com", "name": "중복"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(errorCode("EMAIL_DUPLICATED"));
    }

    // ===== 반대 방향 =====

    @Test
    @DisplayName("서울 관리자도 도쿄 데이터에 접근 불가")
    void reverseDirection() throws Exception {
        mockMvc.perform(get("/api/company/members/{id}", tokyoAdmin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seoulAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        mockMvc.perform(delete("/api/company/departments/{id}", tokyoDept.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seoulAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    // ===== 권한별 접근 =====

    @Test
    @DisplayName("토큰 없음 → 401")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/company/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사원은 기업관리자 API 불가 → 403")
    void employeeCannotUseCompanyApi() throws Exception {
        mockMvc.perform(get("/api/company/members")
                        .header(HttpHeaders.AUTHORIZATION, bearer(seoulEmployee)))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    @Test
    @DisplayName("최고관리자는 회사 API 불가 → 403")
    void superAdminCannotUseCompanyApi() throws Exception {
        Member superAdmin = createMember(null, null, "super@test.com", MemberRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/company/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(superAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    @Test
    @DisplayName("기업관리자는 최고관리자 API 불가 → 403")
    void companyAdminCannotUseAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(seoulAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    // ===== 도우미 =====

    private ResultActions asTokyo(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tokyoAdmin)));
    }

    private static ResultMatcher errorCode(String code) {
        return jsonPath("$.code").value(code);
    }
}