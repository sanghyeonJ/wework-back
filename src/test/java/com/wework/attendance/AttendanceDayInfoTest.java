package com.wework.attendance;

import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttendanceDayInfoTest extends IntegrationTestSupport {

    @Autowired CompanyHolidayRepository companyHolidayRepository;
    @Autowired PublicHolidayRepository publicHolidayRepository;

    Company seoul;
    Company other;
    Member seoulAdmin;
    Member otherAdmin;

    // 올해 3월의 첫 금요일을 기준으로 앞뒤 요일을 계산 (연도를 고정하지 않음)
    LocalDate friday;
    LocalDate saturday;
    LocalDate sunday;
    LocalDate monday;
    LocalDate wednesday;

    @BeforeEach
    void setUp() {
        int year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
        friday = LocalDate.of(year, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        saturday = friday.plusDays(1);
        sunday = friday.plusDays(2);
        monday = friday.plusDays(3);
        wednesday = friday.minusDays(2);

        seoul = createCompany("위워크 서울", "111");
        other = createCompany("다른 회사", "222");
        seoulAdmin = createAdmin(seoul, "admin@seoul.test");
        otherAdmin = createAdmin(other, "admin@other.test");
    }

    private ResultActions request(Member member, LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/attendance/manage/day-info")
                .param("date", date.toString())
                .header("Authorization", bearer(member)));
    }

    @Test
    void 주말_판정은_금토일월을_쌍으로_확인한다() throws Exception {
        // 바로 앞(금)·주말 둘(토·일)·바로 뒤(월): 토·일 중 하나를 빠뜨리거나 평일을 주말로 보는 실수를 잡음
        request(seoulAdmin, friday).andExpect(status().isOk()).andExpect(jsonPath("$.weekend").value(false));
        request(seoulAdmin, saturday).andExpect(jsonPath("$.weekend").value(true));
        request(seoulAdmin, sunday).andExpect(jsonPath("$.weekend").value(true));
        request(seoulAdmin, monday).andExpect(jsonPath("$.weekend").value(false));
    }

    @Test
    void 휴일_목록은_국가_공휴일과_회사_휴일_그리고_겹치는_날을_구분한다() throws Exception {
        publicHolidayRepository.save(PublicHoliday.create("KR", friday, "국가 휴일"));
        companyHolidayRepository.save(CompanyHoliday.create(seoul, monday, "회사 휴일", false));
        // 같은 날에 둘 다
        publicHolidayRepository.save(PublicHoliday.create("KR", wednesday, "겹침 공휴일"));
        companyHolidayRepository.save(CompanyHoliday.create(seoul, wednesday, "겹침 회사 휴일", false));

        request(seoulAdmin, friday)
                .andExpect(jsonPath("$.date").value(friday.toString()))
                .andExpect(jsonPath("$.holidays.length()").value(1))
                .andExpect(jsonPath("$.holidays[0].type").value("PUBLIC"))
                .andExpect(jsonPath("$.holidays[0].name").value("국가 휴일"));

        request(seoulAdmin, monday)
                .andExpect(jsonPath("$.holidays.length()").value(1))
                .andExpect(jsonPath("$.holidays[0].type").value("COMPANY"));

        // 겹치는 날: 둘 다 나오고, 국가 공휴일이 먼저
        request(seoulAdmin, wednesday)
                .andExpect(jsonPath("$.holidays.length()").value(2))
                .andExpect(jsonPath("$.holidays[0].type").value("PUBLIC"))
                .andExpect(jsonPath("$.holidays[1].type").value("COMPANY"));

        // 휴일이 없는 날(위 세 날의 앞뒤): 빈 목록
        request(seoulAdmin, friday.minusDays(1))
                .andExpect(jsonPath("$.holidays.length()").value(0));
        request(seoulAdmin, monday.plusDays(1))
                .andExpect(jsonPath("$.holidays.length()").value(0));
    }

    @Test
    void 반복_회사_휴일은_조회한_날짜로_나온다() throws Exception {
        // 3년 전 같은 월·일로 저장: 저장된 날짜가 아니라 조회한 해의 날짜가 나와야 함
        companyHolidayRepository.save(CompanyHoliday.create(seoul, friday.minusYears(3), "창립기념일", true));

        request(seoulAdmin, friday)
                .andExpect(jsonPath("$.holidays.length()").value(1))
                .andExpect(jsonPath("$.holidays[0].date").value(friday.toString()))
                .andExpect(jsonPath("$.holidays[0].name").value("창립기념일"));

        // 다른 월·일에는 안 나옴
        request(seoulAdmin, monday)
                .andExpect(jsonPath("$.holidays.length()").value(0));
    }

    @Test
    void 주말이면서_휴일인_날은_둘_다_표시한다() throws Exception {
        publicHolidayRepository.save(PublicHoliday.create("KR", saturday, "토요일 공휴일"));

        request(seoulAdmin, saturday)
                .andExpect(jsonPath("$.weekend").value(true))
                .andExpect(jsonPath("$.holidays.length()").value(1));
    }

    @Test
    void 다른_회사의_휴일은_섞이지_않는다() throws Exception {
        companyHolidayRepository.save(CompanyHoliday.create(seoul, friday, "서울 일회성", false));
        companyHolidayRepository.save(CompanyHoliday.create(seoul, monday.minusYears(3), "서울 반복", true));

        request(otherAdmin, friday).andExpect(jsonPath("$.holidays.length()").value(0));
        request(otherAdmin, monday).andExpect(jsonPath("$.holidays.length()").value(0));

        // 같은 요청에 서울 관리자에게는 보임 (비어서 통과하는 테스트 방지)
        request(seoulAdmin, friday).andExpect(jsonPath("$.holidays.length()").value(1));
        request(seoulAdmin, monday).andExpect(jsonPath("$.holidays.length()").value(1));
    }

    @Test
    void 사원과_토큰_없는_요청은_막힌다() throws Exception {
        Department department = createDepartment(seoul, null, "본사");
        Member employee = createEmployee(seoul, department, "employee@seoul.test");

        request(employee, friday)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/attendance/manage/day-info").param("date", friday.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 같은 요청을 기업관리자가 하면 성공 (막힌 이유가 권한이라는 증거)
        request(seoulAdmin, friday).andExpect(status().isOk());
    }

    @Test
    void 날짜를_빼면_400이다() throws Exception {
        mockMvc.perform(get("/api/attendance/manage/day-info")
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isBadRequest());

        request(seoulAdmin, friday).andExpect(status().isOk());
    }
}