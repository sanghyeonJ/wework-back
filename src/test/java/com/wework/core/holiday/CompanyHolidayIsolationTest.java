package com.wework.core.holiday;

import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyHolidayIsolationTest extends IntegrationTestSupport {

    @Autowired
    CompanyHolidayRepository companyHolidayRepository;

    int year;
    Company seoul;
    Company tokyo;
    Member seoulAdmin;
    Member tokyoAdmin;

    @BeforeEach
    void setUp() {
        year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        seoul = createCompany("위워크 서울", "111");
        tokyo = createCompany("위워크 도쿄", "222");
        seoulAdmin = createAdmin(seoul, "admin@seoul.test");
        tokyoAdmin = createAdmin(tokyo, "admin@tokyo.test");
    }

    @Test
    void 목록에_다른_회사_휴일은_나오지_않는다() throws Exception {
        companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year, 12, 1), "서울 창립기념일", false));

        // 도쿄 관리자: 서울 휴일이 보이면 안 됨
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(tokyoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 서울 관리자: 같은 요청에 내 휴일은 보임 (비어서 통과하는 테스트 방지)
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("서울 창립기념일"));
    }

    @Test
    void 목록에_다른_회사의_반복_휴일은_나오지_않는다() throws Exception {
        // 일부러 옛 연도 날짜로 저장: 기간 조건이 아니라 recurring=true 조건으로만 걸리는 경우
        // (쿼리의 or 괄호가 빠지면 도쿄 목록에 이 휴일이 섞여 나옴)
        companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year - 3, 12, 1), "서울 반복 휴일", true));

        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(tokyoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 서울은 올해로 조회해도 반복 휴일이 보임
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recurring").value(true));
    }

    @Test
    void 다른_회사_휴일은_수정할_수_없다() throws Exception {
        CompanyHoliday holiday = companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year, 12, 1), "서울 창립기념일", false));

        String body = """
            { "holidayDate": "%s", "name": "수정된 이름", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 1));

        // 도쿄 관리자: 403이 아니라 404여야 함 (다른 회사 id의 존재 여부를 알려주지 않음)
        mockMvc.perform(patch("/api/company/holidays/{id}", holiday.getId())
                        .header("Authorization", bearer(tokyoAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOLIDAY_NOT_FOUND"));

        // 서울 관리자: 같은 요청이 성공 (날짜를 그대로 둬도 자기 자신은 중복이 아님)
        mockMvc.perform(patch("/api/company/holidays/{id}", holiday.getId())
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정된 이름"));
    }

    @Test
    void 다른_회사_휴일은_삭제할_수_없다() throws Exception {
        CompanyHoliday holiday = companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year, 12, 1), "서울 창립기념일", false));

        mockMvc.perform(delete("/api/company/holidays/{id}", holiday.getId())
                        .header("Authorization", bearer(tokyoAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HOLIDAY_NOT_FOUND"));

        // 지워지지 않았는지 서울 목록으로 확인
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(jsonPath("$.length()").value(1));

        // 서울 관리자는 삭제 성공
        mockMvc.perform(delete("/api/company/holidays/{id}", holiday.getId())
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void 같은_날짜는_같은_회사에서만_중복이다() throws Exception {
        String body = """
            { "holidayDate": "%s", "name": "창립기념일", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 1));

        // 서울이 먼저 등록
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // 도쿄: 같은 날짜여도 다른 회사라 등록 가능
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(tokyoAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // 서울이 같은 날짜를 또 등록: 409
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLIDAY_DATE_DUPLICATED"));
    }

    @Test
    void 수정할_때_같은_회사의_다른_휴일_날짜와_겹치면_409다() throws Exception {
        companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year, 12, 1), "휴일 A", false));
        CompanyHoliday second = companyHolidayRepository.save(
                CompanyHoliday.create(seoul, LocalDate.of(year, 12, 2), "휴일 B", false));

        // B의 날짜를 A의 날짜로 바꾸려 함: 409
        String conflictBody = """
            { "holidayDate": "%s", "name": "휴일 B", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 1));
        mockMvc.perform(patch("/api/company/holidays/{id}", second.getId())
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLIDAY_DATE_DUPLICATED"));

        // 날짜는 그대로 두고 이름만 바꾸면 성공 (자기 자신은 중복이 아님)
        String sameDateBody = """
            { "holidayDate": "%s", "name": "휴일 B 수정", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 2));
        mockMvc.perform(patch("/api/company/holidays/{id}", second.getId())
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameDateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("휴일 B 수정"));
    }

    @Test
    void 사원과_토큰_없는_요청은_막힌다() throws Exception {
        Department department = createDepartment(seoul, null, "본사");
        Member employee = createEmployee(seoul, department, "employee@seoul.test");

        // 사원: 403
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 토큰 없음: 401
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 같은 요청을 기업관리자가 하면 성공 (막힌 이유가 권한이라는 증거)
        mockMvc.perform(get("/api/company/holidays")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void 필수_값이_빠지면_400이다() throws Exception {
        // recurring 누락 (Boolean으로 받은 이유)
        String noRecurring = """
            { "holidayDate": "%s", "name": "창립기념일" }
            """.formatted(LocalDate.of(year, 12, 1));
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noRecurring))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // name이 공백
        String blankName = """
            { "holidayDate": "%s", "name": "   ", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 1));
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // 문제를 뺀 요청은 성공 (400이 입력 검사 때문이라는 증거)
        String valid = """
            { "holidayDate": "%s", "name": "창립기념일", "recurring": false }
            """.formatted(LocalDate.of(year, 12, 1));
        mockMvc.perform(post("/api/company/holidays")
                        .header("Authorization", bearer(seoulAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isCreated());
    }
}