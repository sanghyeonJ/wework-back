package com.wework.attendance;

import com.wework.core.company.Company;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRole;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("근태 규칙")
class AttendanceRuleTest extends IntegrationTestSupport {

    // createCompany가 만드는 회사의 시간대
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private AttendanceRepository attendanceRepository;

    private Company seoul;
    private Member admin;
    private Member employee;
    private Member employee2;

    @BeforeEach
    void setUp() {
        seoul = createCompany("위워크 서울", "111");
        admin = createAdmin(seoul, "admin@test.com");
        employee = createEmployee(seoul, null, "employee@test.com");
        employee2 = createEmployee(seoul, null, "employee2@test.com");
    }

    // ===== 출근·퇴근 =====

    @Test
    @DisplayName("출근을 두 번 눌러도 첫 기록이 그대로 돌아온다 (기록은 1개)")
    void checkInTwiceKeepsFirstRecord() throws Exception {
        String first = as(employee, post("/api/attendance/check-in"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = as(employee, post("/api/attendance/check-in"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, second); // id, checkInAt 모두 같음
        assertEquals(1, attendanceRepository.count());
    }

    @Test
    @DisplayName("출근 기록 없이 퇴근 → 409 NOT_CHECKED_IN")
    void checkOutWithoutCheckIn() throws Exception {
        as(employee, post("/api/attendance/check-out"))
                .andExpect(status().isConflict())
                .andExpect(errorCode("NOT_CHECKED_IN"));
    }

    @Test
    @DisplayName("퇴근은 누를 때마다 덮어쓰고, 조퇴도 그때마다 다시 판단한다")
    void checkOutOverwritesAndRejudgesEarlyLeave() throws Exception {
        as(employee, post("/api/attendance/check-in")).andExpect(status().isOk());

        // 퇴근 기준 23:59 → 지금 퇴근하면 조퇴
        saveCompanySchedule("00:00", "23:59", 0);
        as(employee, post("/api/attendance/check-out"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earlyLeave").value(true));

        Attendance record = findRecord(employee);
        Instant firstCheckOut = record.getCheckOutAt();

        // 퇴근 기준 00:01 → 지금 퇴근하면 조퇴 아님
        saveCompanySchedule("00:00", "00:01", 0);
        as(employee, post("/api/attendance/check-out"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earlyLeave").value(false));

        assertTrue(record.getCheckOutAt().isAfter(firstCheckOut)); // 마지막 퇴근이 유효
        assertFalse(record.isEarlyLeave());                        // 조퇴도 해제됨
        assertEquals(1, attendanceRepository.count());
    }

    @Test
    @DisplayName("지각은 적용 근무시간으로 판단한다: 개인 설정이 회사 설정보다 우선")
    void lateFollowsEffectiveSchedule() throws Exception {
        // 회사: 23:58 출근 + 유예 60분 → 오늘 어느 시각에 출근해도 정상
        saveCompanySchedule("23:58", "23:59", 60);
        // employee만 개인 설정: 00:00 출근 + 유예 0분 → 거의 항상 지각
        as(admin, put("/api/attendance/manage/schedules/members/{id}", employee.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("00:00", "00:01", 0)))
                .andExpect(status().isOk());

        as(employee2, post("/api/attendance/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(false));

        as(employee, post("/api/attendance/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(true));
    }

    @Test
    @DisplayName("근무시간을 나중에 바꿔도 이미 저장된 지각 결과는 그대로다")
    void pastJudgementNotChangedBySchedule() throws Exception {
        saveCompanySchedule("23:58", "23:59", 60);
        as(employee, post("/api/attendance/check-in"))
                .andExpect(jsonPath("$.late").value(false));

        // 이제 설정을 "거의 항상 지각"으로 바꿈
        saveCompanySchedule("00:00", "00:01", 0);

        as(employee, post("/api/attendance/check-in"))
                .andExpect(jsonPath("$.late").value(false));
        as(employee, get("/api/attendance/today"))
                .andExpect(jsonPath("$.attendance.late").value(false));
    }

    // ===== 근무시간 설정 =====

    @Test
    @DisplayName("적용 근무시간 우선순위: 기본값 → 회사 → 개인, 개인 설정 삭제 시 회사로 복귀")
    void effectivePriority() throws Exception {
        effective(employee).andExpect(jsonPath("$.source").value("DEFAULT"))
                .andExpect(jsonPath("$.graceMinutes").value(0));

        saveCompanySchedule("09:30", "18:30", 10);
        effective(employee).andExpect(jsonPath("$.source").value("COMPANY"))
                .andExpect(jsonPath("$.graceMinutes").value(10));

        as(admin, put("/api/attendance/manage/schedules/members/{id}", employee.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("10:00", "19:00", 5)))
                .andExpect(status().isOk());
        effective(employee).andExpect(jsonPath("$.source").value("MEMBER"))
                .andExpect(jsonPath("$.graceMinutes").value(5));

        // 다른 사원은 개인 설정이 없으니 회사 설정
        effective(employee2).andExpect(jsonPath("$.source").value("COMPANY"));

        as(admin, delete("/api/attendance/manage/schedules/members/{id}", employee.getId()))
                .andExpect(status().isNoContent());
        effective(employee).andExpect(jsonPath("$.source").value("COMPANY"))
                .andExpect(jsonPath("$.graceMinutes").value(10));
    }

    @Test
    @DisplayName("회사 설정은 한 번 더 저장해도 행이 늘지 않고 값만 바뀐다")
    void companyScheduleSaveIsUpsert() throws Exception {
        saveCompanySchedule("09:00", "18:00", 10);
        saveCompanySchedule("09:30", "18:30", 20);

        as(admin, get("/api/attendance/manage/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].graceMinutes").value(20));
    }

    @Test
    @DisplayName("퇴근 기준이 출근 기준보다 같거나 이르면 → 400 INVALID_WORK_TIME")
    void invalidWorkTime() throws Exception {
        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("18:00", "09:00", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_WORK_TIME"));

        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("09:00", "09:00", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_WORK_TIME"));
    }

    @Test
    @DisplayName("유예 분: 없음·음수·181 → 400 INVALID_INPUT, 0과 180은 허용")
    void graceMinutesRange() throws Exception {
        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startTime": "09:00", "endTime": "18:00"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_INPUT"));

        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("09:00", "18:00", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_INPUT"));

        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("09:00", "18:00", 61)))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("INVALID_INPUT"));

        saveCompanySchedule("09:00", "18:00", 0);
        saveCompanySchedule("09:00", "18:00", 60);
    }

    @Test
    @DisplayName("퇴사자에게 개인 근무시간 저장 → 409 MEMBER_RESIGNED")
    void resignedMemberSchedule() throws Exception {
        employee2.resign(today().minusDays(1));

        as(admin, put("/api/attendance/manage/schedules/members/{id}", employee2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("09:00", "18:00", 0)))
                .andExpect(status().isConflict())
                .andExpect(errorCode("MEMBER_RESIGNED"));
    }

    // ===== 관리자 근태 조회: 재직 판단 =====

    @Test
    @DisplayName("근태 조회는 그날 재직 중이던 사원만 나온다 (입사 전·퇴사 후·초대만 된 사원 제외)")
    void recordsEmploymentRules() throws Exception {
        LocalDate today = today();

        Member notYetHired = createEmployee(seoul, null, "not-hired@test.com");
        notYetHired.update(notYetHired.getName(), null, null, null, today.plusDays(1));

        Member resignedToday = createEmployee(seoul, null, "resigned-today@test.com");
        resignedToday.resign(today);

        Member resignedYesterday = createEmployee(seoul, null, "resigned-yesterday@test.com");
        resignedYesterday.resign(today.minusDays(1));

        // 비밀번호 설정 전(INVITED) 사원
        Member invited = memberRepository.save(Member.builder()
                .company(seoul).email("invited@test.com").name("invited").role(MemberRole.EMPLOYEE).build());

        // 오늘
        as(admin, get("/api/attendance/manage/records").param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(listsMember(admin))
                .andExpect(listsMember(employee))
                .andExpect(listsMember(resignedToday))      // 퇴사일 당일은 재직
                .andExpect(notListsMember(resignedYesterday))
                .andExpect(notListsMember(notYetHired))
                .andExpect(notListsMember(invited));

        // 어제
        as(admin, get("/api/attendance/manage/records").param("date", today.minusDays(1).toString()))
                .andExpect(listsMember(resignedYesterday))  // 그날은 재직 중이었음
                .andExpect(listsMember(resignedToday))
                .andExpect(notListsMember(notYetHired));

        // 내일
        as(admin, get("/api/attendance/manage/records").param("date", today.plusDays(1).toString()))
                .andExpect(listsMember(notYetHired))        // 입사일 당일부터 재직
                .andExpect(notListsMember(resignedToday));
    }

    // ===== 내 월별 조회 =====

    @Test
    @DisplayName("월별 조회는 그 달 1일~말일만, 날짜 오름차순으로 나온다")
    void monthlyBoundary() throws Exception {
        YearMonth month = YearMonth.now(ZONE);
        saveRecord(employee, month.minusMonths(1).atEndOfMonth()); // 전달 말일 → 제외
        saveRecord(employee, month.atEndOfMonth());                // 말일 → 포함
        saveRecord(employee, month.atDay(1));                      // 1일 → 포함
        saveRecord(employee, month.plusMonths(1).atDay(1));        // 다음 달 1일 → 제외

        as(employee, get("/api/attendance/me")
                .param("year", String.valueOf(month.getYear()))
                .param("month", String.valueOf(month.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].workDate").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$[1].workDate").value(month.atEndOfMonth().toString()));
    }

    @Test
    @DisplayName("월이 0이나 13이면 → 400 INVALID_INPUT")
    void monthlyInvalidMonth() throws Exception {
        for (String month : new String[]{"0", "13"}) {
            as(employee, get("/api/attendance/me").param("year", "2026").param("month", month))
                    .andExpect(status().isBadRequest())
                    .andExpect(errorCode("INVALID_INPUT"));
        }
    }

    // ===== 회사 시간대 =====

    @Test
    @DisplayName("근무 날짜는 회사 시간대로 계산한다 (로스앤젤레스)")
    void workDateLosAngeles() throws Exception {
        assertWorkDateFollowsCompanyZone("901", "US", "America/Los_Angeles");
    }

    @Test
    @DisplayName("근무 날짜는 회사 시간대로 계산한다 (오클랜드)")
    void workDateAuckland() throws Exception {
        assertWorkDateFollowsCompanyZone("902", "NZ", "Pacific/Auckland");
    }

    // ===== 도우미 =====

    private void assertWorkDateFollowsCompanyZone(String businessNumber, String countryCode, String zoneId) throws Exception {
        ZoneId zone = ZoneId.of(zoneId);
        Company company = companyRepository.save(Company.create("시간대 회사 " + businessNumber, businessNumber, countryCode, zoneId));
        Member member = createEmployee(company, null, "zone-" + businessNumber + "@test.com");

        as(member, post("/api/attendance/check-in")).andExpect(status().isOk());

        Attendance record = findRecord(member);
        // 근무 날짜 = 출근 시각을 "회사 시간대"로 바꾼 날짜
        assertEquals(record.getCheckInAt().atZone(zone).toLocalDate(), record.getWorkDate());

        as(member, get("/api/attendance/today"))
                .andExpect(jsonPath("$.workDate").value(record.getWorkDate().toString()));
    }

    private Attendance findRecord(Member member) {
        return attendanceRepository.findAll().stream()
                .filter(a -> a.getMember().getId().equals(member.getId()))
                .findFirst()
                .orElseThrow();
    }

    private void saveRecord(Member member, LocalDate workDate) {
        attendanceRepository.save(Attendance.create(
                member.getCompany(), member, workDate, workDate.atTime(9, 0).atZone(ZONE).toInstant(), false));
    }

    private void saveCompanySchedule(String start, String end, int graceMinutes) throws Exception {
        as(admin, put("/api/attendance/manage/schedules/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(start, end, graceMinutes)))
                .andExpect(status().isOk());
    }

    private ResultActions effective(Member target) throws Exception {
        return as(admin, get("/api/attendance/manage/schedules/effective")
                .param("memberId", target.getId().toString()))
                .andExpect(status().isOk());
    }

    private static String body(String start, String end, int graceMinutes) {
        return """
                {"startTime": "%s", "endTime": "%s", "graceMinutes": %d}
                """.formatted(start, end, graceMinutes);
    }

    private ResultActions as(Member member, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // 목록(JSON 배열)에 그 사원이 있다 / 없다
    private static ResultMatcher listsMember(Member member) {
        return jsonPath("$[?(@.memberId == " + member.getId() + ")]").isNotEmpty();
    }

    private static ResultMatcher notListsMember(Member member) {
        return jsonPath("$[?(@.memberId == " + member.getId() + ")]").isEmpty();
    }

    private static ResultMatcher errorCode(String code) {
        return jsonPath("$.code").value(code);
    }
}