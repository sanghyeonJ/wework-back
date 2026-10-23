package com.wework.attendance;

import com.wework.core.company.Company;
import com.wework.core.department.Department;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("근태 모듈 회사 데이터 격리")
class AttendanceIsolationTest extends IntegrationTestSupport {

    // createCompany가 만드는 회사의 시간대 (IntegrationTestSupport 기준)
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final String SCHEDULE_BODY = """
            {"startTime": "09:30", "endTime": "18:30", "graceMinutes": 10}
            """;

    private Member seoulAdmin;
    private Member tokyoAdmin;
    private Member seoulEmployee;
    private Member tokyoEmployee;
    private Department seoulDept;
    private Department tokyoDept;

    @BeforeEach
    void setUp() {
        Company seoul = createCompany("위워크 서울", "111");
        Company tokyo = createCompany("위워크 도쿄", "222");

        seoulAdmin = createAdmin(seoul, "seoul-admin@test.com");
        tokyoAdmin = createAdmin(tokyo, "tokyo-admin@test.com");

        seoulDept = createDepartment(seoul, null, "개발팀");
        tokyoDept = createDepartment(tokyo, null, "본사");

        seoulEmployee = createEmployee(seoul, seoulDept, "seoul-employee@test.com");
        tokyoEmployee = createEmployee(tokyo, tokyoDept, "tokyo-employee@test.com");
    }

    // ===== 근무시간 설정 =====

    @Test
    @DisplayName("다른 회사 사원에게 개인 근무시간 저장 → 404")
    void saveMemberScheduleOtherCompany() throws Exception {
        as(tokyoAdmin, put("/api/attendance/manage/schedules/members/{id}", seoulEmployee.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회사 사원의 개인 설정 삭제 → 404 MEMBER_NOT_FOUND (설정 없음 404가 먼저 나오면 안 됨)")
    void deleteMemberScheduleOtherCompany() throws Exception {
        // 서울 사원의 개인 설정이 실제로 있는 상태를 만든다
        as(seoulAdmin, put("/api/attendance/manage/schedules/members/{id}", seoulEmployee.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULE_BODY))
                .andExpect(status().isOk());

        as(tokyoAdmin, delete("/api/attendance/manage/schedules/members/{id}", seoulEmployee.getId()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));

        // 서울 설정은 그대로 남아 있다
        as(seoulAdmin, get("/api/attendance/manage/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("다른 회사 사원의 적용 근무시간 조회 → 404")
    void effectiveOtherCompany() throws Exception {
        as(tokyoAdmin, get("/api/attendance/manage/schedules/effective")
                .param("memberId", seoulEmployee.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("근무시간 설정 목록에 다른 회사 설정이 안 보인다")
    void scheduleListIsolated() throws Exception {
        as(seoulAdmin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULE_BODY))
                .andExpect(status().isOk());
        as(seoulAdmin, put("/api/attendance/manage/schedules/members/{id}", seoulEmployee.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULE_BODY))
                .andExpect(status().isOk());

        as(seoulAdmin, get("/api/attendance/manage/schedules"))
                .andExpect(jsonPath("$.length()").value(2));

        as(tokyoAdmin, get("/api/attendance/manage/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("서울 회사 설정이 있어도 도쿄 사원은 기본값이 적용된다")
    void companyScheduleNotShared() throws Exception {
        as(seoulAdmin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SCHEDULE_BODY))
                .andExpect(status().isOk());

        as(tokyoAdmin, get("/api/attendance/manage/schedules/effective")
                .param("memberId", tokyoEmployee.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    // ===== 관리자 근태 조회 =====

    @Test
    @DisplayName("근태 조회에 다른 회사 부서 id → 404")
    void recordsOtherCompanyDepartment() throws Exception {
        as(tokyoAdmin, get("/api/attendance/manage/records")
                .param("date", today().toString())
                .param("departmentId", seoulDept.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));

        // 하위 부서 포함 옵션을 켜도 같다 (확인이 하위 부서 계산보다 먼저)
        as(tokyoAdmin, get("/api/attendance/manage/records")
                .param("date", today().toString())
                .param("departmentId", seoulDept.getId().toString())
                .param("includeSubDepartments", "true"))
                .andExpect(status().isNotFound())
                .andExpect(errorCode("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("근태 조회는 자기 회사 사원만 나오고, 다른 회사 기록이 안 붙는다")
    void recordsIsolated() throws Exception {
        // 서울 사원만 출근한다
        as(seoulEmployee, post("/api/attendance/check-in")).andExpect(status().isOk());

        // 서울: 사원 2명(이름순: seoul-admin, seoul-employee), 출근한 사람만 기록 있음
        as(seoulAdmin, get("/api/attendance/manage/records").param("date", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].memberId").value(seoulAdmin.getId()))
                .andExpect(jsonPath("$[0].attendance").isEmpty())
                .andExpect(jsonPath("$[1].memberId").value(seoulEmployee.getId()))
                .andExpect(jsonPath("$[1].attendance").isNotEmpty());

        // 도쿄: 사원 2명, 둘 다 기록 없음 (서울 사원의 기록이 섞이면 안 됨)
        as(tokyoAdmin, get("/api/attendance/manage/records").param("date", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].memberId").value(tokyoAdmin.getId()))
                .andExpect(jsonPath("$[0].attendance").isEmpty())
                .andExpect(jsonPath("$[1].memberId").value(tokyoEmployee.getId()))
                .andExpect(jsonPath("$[1].attendance").isEmpty());
    }

    // ===== 사원 본인 조회 =====

    @Test
    @DisplayName("다른 회사 사원의 출근 기록이 내 오늘·월별 조회에 안 보인다")
    void myRecordsIsolated() throws Exception {
        as(seoulEmployee, post("/api/attendance/check-in")).andExpect(status().isOk());

        // 확인용: 서울 사원 본인에게는 보인다 (테스트가 빈 결과로 우연히 통과하는 것을 막음)
        as(seoulEmployee, get("/api/attendance/me")
                .param("year", String.valueOf(today().getYear()))
                .param("month", String.valueOf(today().getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // 도쿄 사원에게는 안 보인다
        as(tokyoEmployee, get("/api/attendance/me")
                .param("year", String.valueOf(today().getYear()))
                .param("month", String.valueOf(today().getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        as(tokyoEmployee, get("/api/attendance/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendance").isEmpty());
    }

    // ===== 권한별 접근 =====

    @Test
    @DisplayName("토큰 없이 출근 → 401")
    void noToken() throws Exception {
        mockMvc.perform(post("/api/attendance/check-in"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사원은 근태 관리 API 불가 → 403")
    void employeeCannotUseManageApi() throws Exception {
        as(seoulEmployee, get("/api/attendance/manage/schedules"))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));

        as(seoulEmployee, get("/api/attendance/manage/records").param("date", today().toString()))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    @Test
    @DisplayName("최고관리자는 출근·근태 관리 API 모두 불가 → 403")
    void superAdminCannotUseAttendanceApi() throws Exception {
        Member superAdmin = createMember(null, null, "super@test.com", MemberRole.SUPER_ADMIN);

        as(superAdmin, post("/api/attendance/check-in"))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));

        as(superAdmin, get("/api/attendance/manage/schedules"))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    // ===== 상태 =====

    @Test
    @DisplayName("퇴사자는 출근 불가 → 409 (퇴사 후에도 토큰이 살아 있는 경우)")
    void resignedMemberCannotCheckIn() throws Exception {
        seoulEmployee.resign(today().minusDays(1)); // 엔티티를 직접 바꿔 상황 준비 (같은 트랜잭션)

        as(seoulEmployee, post("/api/attendance/check-in"))
                .andExpect(status().isConflict())
                .andExpect(errorCode("MEMBER_RESIGNED"));
    }

    // ===== 도우미 =====

    private ResultActions as(Member member, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    // 날짜는 고정값 대신 회사 시간대 기준 오늘로 계산 (규칙)
    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static ResultMatcher errorCode(String code) {
        return jsonPath("$.code").value(code);
    }
}