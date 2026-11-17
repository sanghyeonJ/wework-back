package com.wework.core.leave;

import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaveGrantRuleApiTest extends IntegrationTestSupport {

    private static final String URL = "/api/leave/manage/grant-rules";

    private String adminA;     // 회사 A 기업관리자 토큰
    private String adminB;     // 회사 B 기업관리자 토큰
    private String employeeA;  // 회사 A 일반 사원 토큰

    @BeforeEach
    void prepareData() {
        // PUT을 부르면 영속성 컨텍스트가 비워지므로 모든 데이터는 여기서 먼저 만든다
        var companyA = createCompany("지급설정A", "9990000001");
        var companyB = createCompany("지급설정B", "9990000002");
        var departmentA = createDepartment(companyA, null, "개발팀");

        adminA = bearer(createAdmin(companyA, "grant-admin-a@test.com"));
        adminB = bearer(createAdmin(companyB, "grant-admin-b@test.com"));
        employeeA = bearer(createEmployee(companyA, departmentA, "grant-emp-a@test.com"));
    }

    // ---------- 도우미 ----------

    private ResultActions getRules(String auth) throws Exception {
        return mockMvc.perform(get(URL).header("Authorization", auth));
    }

    private ResultActions putRules(String auth, String json) throws Exception {
        return mockMvc.perform(put(URL)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String rule(int serviceYear, String grantedDays) {
        return "{\"serviceYear\":" + serviceYear + ",\"grantedDays\":" + grantedDays + "}";
    }

    private static String body(String... rules) {
        return "{\"rules\":[" + String.join(",", rules) + "]}";
    }

    // 숫자 비교는 15.0처럼 소수로 쓴다 (응답이 15든 15.0이든 같은 값으로 비교됨)

    // ---------- 조회·저장 ----------

    @Test
    @DisplayName("설정이 없으면 빈 목록")
    void emptyWhenNothingSaved() throws Exception {
        getRules(adminA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("저장하면 저장된 표가 돌아오고 GET도 같다")
    void saveReturnsSavedTable() throws Exception {
        putRules(adminA, body(rule(1, "15"), rule(3, "16")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceYear").value(1))
                .andExpect(jsonPath("$[0].grantedDays").value(15.0))
                .andExpect(jsonPath("$[1].serviceYear").value(3))
                .andExpect(jsonPath("$[1].grantedDays").value(16.0));

        getRules(adminA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceYear").value(1))
                .andExpect(jsonPath("$[0].grantedDays").value(15.0))
                .andExpect(jsonPath("$[1].serviceYear").value(3))
                .andExpect(jsonPath("$[1].grantedDays").value(16.0));
    }

    @Test
    @DisplayName("역순으로 보내도 근속연수 오름차순으로 나온다")
    void sortedByServiceYear() throws Exception {
        putRules(adminA, body(rule(3, "16"), rule(1, "15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceYear").value(1))
                .andExpect(jsonPath("$[1].serviceYear").value(3));
    }

    @Test
    @DisplayName("같은 근속연수를 다시 저장해도 성공하고 새 표로 교체된다")
    void resaveSameServiceYear() throws Exception {
        putRules(adminA, body(rule(1, "15"), rule(3, "16")))
                .andExpect(status().isOk());

        // 1년을 그대로 두고, 3년을 고치고, 5년을 추가
        putRules(adminA, body(rule(1, "15"), rule(3, "17"), rule(5, "17")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].serviceYear").value(3))
                .andExpect(jsonPath("$[1].grantedDays").value(17.0));

        getRules(adminA)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].serviceYear").value(5));
    }

    @Test
    @DisplayName("빈 목록을 저장하면 설정이 모두 지워진다")
    void saveEmptyClearsTable() throws Exception {
        putRules(adminA, body(rule(1, "15"), rule(3, "16")))
                .andExpect(status().isOk());

        putRules(adminA, body())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        getRules(adminA)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("근속연수 1과 50은 저장된다 (경계)")
    void boundaryServiceYearsSaved() throws Exception {
        putRules(adminA, body(rule(1, "15"), rule(50, "25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].serviceYear").value(50));
    }

    // ---------- 입력 검사 (서비스) ----------

    @Test
    @DisplayName("근속연수가 중복이면 400이고 기존 표는 그대로")
    void duplicateKeepsOldTable() throws Exception {
        putRules(adminA, body(rule(1, "15")))
                .andExpect(status().isOk());

        putRules(adminA, body(rule(1, "15"), rule(1, "16")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERVICE_YEAR"));

        getRules(adminA)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceYear").value(1))
                .andExpect(jsonPath("$[0].grantedDays").value(15.0));
    }

    @Test
    @DisplayName("근속연수 0은 400")
    void serviceYearZero() throws Exception {
        putRules(adminA, body(rule(0, "15")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_YEAR"));
    }

    @Test
    @DisplayName("근속연수 51은 400")
    void serviceYearFiftyOne() throws Exception {
        putRules(adminA, body(rule(51, "25")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_YEAR"));
    }

    @Test
    @DisplayName("지급 일수가 0.5 단위가 아니면 400")
    void grantedDaysNotHalfUnit() throws Exception {
        putRules(adminA, body(rule(1, "10.3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_DAYS"));
    }

    @Test
    @DisplayName("목록 안에 null이 있으면 400 INVALID_INPUT")
    void nullItemRejected() throws Exception {
        putRules(adminA, "{\"rules\":[null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ---------- 입력 검사 (DTO @NotNull·@Valid, 상태 400만 확인) ----------

    @Test
    @DisplayName("rules가 없으면 400")
    void missingRules() throws Exception {
        putRules(adminA, "{}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("항목에 serviceYear가 없으면 400 (안쪽 @Valid 동작)")
    void missingServiceYear() throws Exception {
        putRules(adminA, "{\"rules\":[{\"grantedDays\":15}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("항목에 grantedDays가 없으면 400 (안쪽 @Valid 동작)")
    void missingGrantedDays() throws Exception {
        putRules(adminA, "{\"rules\":[{\"serviceYear\":1}]}")
                .andExpect(status().isBadRequest());
    }

    // ---------- 회사 격리 ----------

    @Test
    @DisplayName("회사끼리 표가 섞이지 않고, 한 회사가 지워도 다른 회사는 그대로")
    void companiesAreIsolated() throws Exception {
        // 두 회사 모두 1년 행이 있어도 유일 제약에 안 걸린다 (회사 + 근속연수)
        putRules(adminA, body(rule(1, "15")))
                .andExpect(status().isOk());
        putRules(adminB, body(rule(1, "20"), rule(5, "25")))
                .andExpect(status().isOk());

        getRules(adminA)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].grantedDays").value(15.0));
        getRules(adminB)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].grantedDays").value(20.0))
                .andExpect(jsonPath("$[1].serviceYear").value(5));

        // 회사 A가 설정을 지워도 회사 B의 표는 그대로여야 한다 (삭제 쿼리의 where 확인)
        putRules(adminA, body())
                .andExpect(status().isOk());

        getRules(adminA)
                .andExpect(jsonPath("$.length()").value(0));
        getRules(adminB)
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("사원은 조회할 수 없다 (403)")
    void employeeCannotGet() throws Exception {
        getRules(employeeA).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사원은 저장할 수 없다 (403)")
    void employeeCannotPut() throws Exception {
        putRules(employeeA, body(rule(1, "15"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("토큰이 없으면 조회할 수 없다 (401)")
    void noTokenGet() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰이 없으면 저장할 수 없다 (401)")
    void noTokenPut() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rule(1, "15"))))
                .andExpect(status().isUnauthorized());
    }
}