package com.wework.core.holiday;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.department.entity.Department;
import com.wework.core.holiday.dto.HolidayResponse;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import com.wework.core.holiday.service.HolidayCalendarService;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HolidayCalendarTest extends IntegrationTestSupport {

    @Autowired CompanyHolidayRepository companyHolidayRepository;
    @Autowired PublicHolidayRepository publicHolidayRepository;
    @Autowired CompanyRepository companies;
    @Autowired HolidayCalendarService holidayCalendarService;

    int year;
    Company seoul;
    Company other;       // 같은 한국(KR) 회사, 격리 확인용
    Member seoulAdmin;
    Member otherAdmin;

    @BeforeEach
    void setUp() {
        year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        seoul = createCompany("위워크 서울", "111");
        other = createCompany("다른 회사", "222");
        seoulAdmin = createAdmin(seoul, "admin@seoul.test");
        otherAdmin = createAdmin(other, "admin@other.test");
    }

    private void saveCompanyHoliday(Company company, LocalDate date, String name, boolean recurring) {
        companyHolidayRepository.save(CompanyHoliday.create(company, date, name, recurring));
    }

    private void savePublicHoliday(String country, LocalDate date, String name) {
        publicHolidayRepository.save(PublicHoliday.create(country, date, name));
    }

    private ResultActions request(Member member, int queryYear) throws Exception {
        return mockMvc.perform(get("/api/holidays")
                .param("year", String.valueOf(queryYear))
                .header("Authorization", bearer(member)));
    }

    @Test
    void 다른_회사의_일회성과_반복_휴일은_섞이지_않는다() throws Exception {
        saveCompanyHoliday(seoul, LocalDate.of(year, 12, 1), "서울 일회성", false);
        // 옛 연도 날짜로 저장: 기간 조건이 아니라 recurring 조건으로만 걸리는 경우
        saveCompanyHoliday(seoul, LocalDate.of(year - 3, 12, 2), "서울 반복", true);

        request(otherAdmin, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 같은 요청에 내 휴일은 보임 (비어서 통과하는 테스트 방지)
        request(seoulAdmin, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 반복_휴일은_조회한_해의_날짜로_바뀌어_나온다() throws Exception {
        saveCompanyHoliday(seoul, LocalDate.of(year - 3, 12, 1), "창립기념일", true);
        saveCompanyHoliday(seoul, LocalDate.of(year, 12, 2), "일회성", false);

        request(seoulAdmin, year)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(LocalDate.of(year, 12, 1).toString()))
                .andExpect(jsonPath("$[0].name").value("창립기념일"))
                .andExpect(jsonPath("$[0].type").value("COMPANY"))
                .andExpect(jsonPath("$[1].date").value(LocalDate.of(year, 12, 2).toString()));

        // 다음 해: 반복은 따라오고 일회성은 등록한 해에만 나옴
        request(seoulAdmin, year + 1)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value(LocalDate.of(year + 1, 12, 1).toString()));
    }

    @Test
    void 조회_기간_밖의_반복_휴일은_나오지_않는다() {
        // 조회 기간: 6/1 ~ 12/30. 경계는 바로 앞·바로 뒤를 쌍으로 확인
        saveCompanyHoliday(seoul, LocalDate.of(year - 1, 5, 31), "시작 전날", true);
        saveCompanyHoliday(seoul, LocalDate.of(year - 1, 6, 1), "시작일", true);
        saveCompanyHoliday(seoul, LocalDate.of(year - 1, 12, 30), "종료일", true);
        saveCompanyHoliday(seoul, LocalDate.of(year - 1, 12, 31), "종료 다음날", true);

        List<HolidayResponse> result = holidayCalendarService.getHolidays(
                seoul.getId(), LocalDate.of(year, 6, 1), LocalDate.of(year, 12, 30));

        assertEquals(2, result.size());
        assertEquals(LocalDate.of(year, 6, 1), result.get(0).getDate());
        assertEquals(LocalDate.of(year, 12, 30), result.get(1).getDate());
    }

    @Test
    void 윤일_반복_휴일은_평년에는_나오지_않고_윤년에는_나온다() {
        // 윤년 여부는 고정된 사실이라 이 테스트만 연도를 직접 정함
        saveCompanyHoliday(seoul, LocalDate.of(2020, 2, 29), "윤일 기념일", true);

        assertEquals(0, holidayCalendarService.getYearHolidays(seoul.getId(), 2026).size());
        assertEquals(0, holidayCalendarService.getYearHolidays(seoul.getId(), 2027).size());

        List<HolidayResponse> leapYear = holidayCalendarService.getYearHolidays(seoul.getId(), 2028);
        assertEquals(1, leapYear.size());
        assertEquals(LocalDate.of(2028, 2, 29), leapYear.get(0).getDate());
    }

    @Test
    void 한국_외_국가의_회사는_국가_공휴일_없이_회사_휴일만_나온다() throws Exception {
        Company tokyo = companies.save(Company.create("위워크 도쿄", "333", "JP", "Asia/Tokyo"));
        Member tokyoAdmin = createAdmin(tokyo, "admin@tokyo.test");
        savePublicHoliday("KR", LocalDate.of(year, 5, 5), "어린이날");
        saveCompanyHoliday(tokyo, LocalDate.of(year, 12, 1), "도쿄 창립기념일", false);

        request(tokyoAdmin, year)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMPANY"));

        // 한국 회사는 같은 국가 공휴일이 보임
        request(seoulAdmin, year)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PUBLIC"))
                .andExpect(jsonPath("$[0].name").value("어린이날"));
    }

    @Test
    void 같은_날의_국가_공휴일과_회사_휴일은_둘_다_나오고_순서가_고정된다() throws Exception {
        savePublicHoliday("KR", LocalDate.of(year, 5, 5), "어린이날");
        saveCompanyHoliday(seoul, LocalDate.of(year, 5, 5), "회사 행사일", false);
        saveCompanyHoliday(seoul, LocalDate.of(year, 1, 2), "시무식", false);

        request(seoulAdmin, year)
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("시무식"))      // 날짜가 빠른 순
                .andExpect(jsonPath("$[1].type").value("PUBLIC"))      // 같은 날은 국가 공휴일이 먼저
                .andExpect(jsonPath("$[2].type").value("COMPANY"));
    }

    @Test
    void 사원은_조회할_수_있고_토큰_없는_요청은_막힌다() throws Exception {
        Department department = createDepartment(seoul, null, "본사");
        Member employee = createEmployee(seoul, department, "employee@seoul.test");

        request(employee, year).andExpect(status().isOk());

        mockMvc.perform(get("/api/holidays").param("year", String.valueOf(year)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}