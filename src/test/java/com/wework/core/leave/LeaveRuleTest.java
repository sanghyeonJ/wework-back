package com.wework.core.leave;

import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import org.hamcrest.Matchers;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

/**
 * 연차 잔액 규칙 통합 테스트: 회사 격리, 입력 규칙, 권한.
 * 테스트 데이터는 API가 아니라 리포지토리·엔티티로 직접 준비한다 (실패 원인이 섞이지 않게).
 */
class LeaveRuleTest extends IntegrationTestSupport {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired
    private PublicHolidayRepository publicHolidayRepository;
    @Autowired
    private CompanyHolidayRepository companyHolidayRepository;

    // 올해 3월의 첫 월요일 기준 (고정 날짜 대신 계산해서 해가 바뀌어도 같은 요일 구성)
    private static final LocalDate MONDAY = LocalDate.of(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear(), 3, 1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate SATURDAY = MONDAY.plusDays(5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    private Member alice;   // 목록 테스트용
    private Member carol;

    private Company companyA;
    private Company companyB;
    private Member adminA;
    private Member employeeA;
    private Member employeeB;

    @BeforeEach
    void setUp() {
        companyA = createCompany("A회사", "111-11-11111");
        companyB = createCompany("B회사", "222-22-22222");
        var deptA = createDepartment(companyA, null, "개발팀");
        var deptB = createDepartment(companyB, null, "개발팀");
        adminA = createAdmin(companyA, "admin-a@test.com");
        employeeA = createEmployee(companyA, deptA, "emp-a@test.com");
        employeeB = createEmployee(companyB, deptB, "emp-b@test.com");
    }

    // ===== 회사 격리 =====

    @Test
    @DisplayName("다른 회사 사원에게 입력하면 404이고 저장되지 않으며, 내 회사 사원에게는 성공한다")
    void saveBalance_otherCompanyMember() throws Exception {
        int year = thisYear();

        putBalance(adminA, employeeB.getId(), year, "15")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        assertThat(leaveBalanceRepository
                .findByCompanyIdAndMemberIdAndYear(companyB.getId(), employeeB.getId(), year)).isEmpty();

        putBalance(adminA, employeeA.getId(), year, "15")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사원은 자기 장부만 본다 (같은 연도라도 다른 회사 사원의 장부는 섞이지 않는다)")
    void getMyBalance_ownOnly() throws Exception {
        int year = thisYear();
        leaveBalanceRepository.save(LeaveBalance.create(companyA, employeeA, year, new BigDecimal("15")));
        leaveBalanceRepository.save(LeaveBalance.create(companyB, employeeB, year, new BigDecimal("20")));

        getBalance(employeeA, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(15.0));
        getBalance(employeeB, year)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(20.0));
    }

    // ===== 입력과 조회 =====

    @Test
    @DisplayName("처음 입력하면 장부가 생기고, 다시 입력하면 같은 행이 수정된다 (행은 하나)")
    void saveBalance_createThenUpdate() throws Exception {
        int year = thisYear();

        putBalance(adminA, employeeA.getId(), year, "15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(year))
                .andExpect(jsonPath("$.grantedDays").value(15.0))
                .andExpect(jsonPath("$.usedDays").value(0.0))
                .andExpect(jsonPath("$.remainingDays").value(15.0));

        putBalance(adminA, employeeA.getId(), year, "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(20.0))
                .andExpect(jsonPath("$.remainingDays").value(20.0));

        assertThat(leaveBalanceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("장부가 없는 해를 조회하면 에러 없이 지급·사용·잔여 모두 0일로 응답한다")
    void getMyBalance_noRow() throws Exception {
        getBalance(employeeA, thisYear())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(0.0))
                .andExpect(jsonPath("$.usedDays").value(0.0))
                .andExpect(jsonPath("$.remainingDays").value(0.0));
    }

    @Test
    @DisplayName("관리자가 입력한 값이 그 사원의 조회에 나오고, 다른 연도는 0일이다")
    void getMyBalance_showsSavedValue() throws Exception {
        int year = thisYear();
        putBalance(adminA, employeeA.getId(), year, "15").andExpect(status().isOk());

        getBalance(employeeA, year)
                .andExpect(jsonPath("$.grantedDays").value(15.0))
                .andExpect(jsonPath("$.remainingDays").value(15.0));
        getBalance(employeeA, year + 1)
                .andExpect(jsonPath("$.grantedDays").value(0.0));
    }

    // ===== 입력 규칙 =====

    @Test
    @DisplayName("지급 일수를 사용 일수보다 낮추면 409, 같게 맞추면 성공한다 (경계 쌍)")
    void saveBalance_belowUsed() throws Exception {
        int year = thisYear();
        LeaveBalance balance = leaveBalanceRepository.save(
                LeaveBalance.create(companyA, employeeA, year, new BigDecimal("15")));
        // 사용 일수를 올리는 기능은 3-4(휴가 승인)에서 생기므로, 지금은 필드를 직접 바꿔 준비한다
        ReflectionTestUtils.setField(balance, "usedDays", new BigDecimal("10"));

        putBalance(adminA, employeeA.getId(), year, "9.5")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRANTED_DAYS_BELOW_USED"));
        assertThat(balance.getGrantedDays()).isEqualByComparingTo("15");   // 거절되면 값이 그대로

        putBalance(adminA, employeeA.getId(), year, "10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingDays").value(0.0));
    }

    @Test
    @DisplayName("퇴사자에게 입력하면 409 MEMBER_RESIGNED이고 저장되지 않으며, 재직 사원은 같은 요청이 성공한다")
    void saveBalance_resignedMember() throws Exception {
        int year = thisYear();
        Member resigned = createEmployee(companyA, employeeA.getDepartment(), "resigned@test.com");
        resigned.resign(LocalDate.now(ZoneId.of("Asia/Seoul")));

        putBalance(adminA, resigned.getId(), year, "15")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_RESIGNED"));
        assertThat(leaveBalanceRepository
                .findByCompanyIdAndMemberIdAndYear(companyA.getId(), resigned.getId(), year)).isEmpty();

        putBalance(adminA, employeeA.getId(), year, "15")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 사원 id는 404 MEMBER_NOT_FOUND이다")
    void saveBalance_memberNotFound() throws Exception {
        putBalance(adminA, 999999L, thisYear(), "15")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("일수 검사: 100·0은 성공, 100.5·-0.5·10.3은 400 INVALID_LEAVE_DAYS, 값을 안 보내면 400 INVALID_INPUT")
    void saveBalance_daysValidation() throws Exception {
        int year = thisYear();
        Long memberId = employeeA.getId();

        putBalance(adminA, memberId, year, "100").andExpect(status().isOk());
        putBalance(adminA, memberId, year, "0").andExpect(status().isOk());   // 0은 지급 취소 용도라 허용

        for (String invalid : new String[]{"100.5", "-0.5", "10.3"}) {
            putBalance(adminA, memberId, year, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LEAVE_DAYS"));
        }

        // @Valid가 빠지면 null이 서비스까지 들어가 500이 된다
        putBalanceRaw(adminA, memberId, year, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("연도 경계: 2015·내년은 성공, 2014·내후년은 400 INVALID_LEAVE_YEAR")
    void saveBalance_yearBoundary() throws Exception {
        int thisYear = thisYear();
        Long memberId = employeeA.getId();

        putBalance(adminA, memberId, 2015, "15").andExpect(status().isOk());
        putBalance(adminA, memberId, thisYear + 1, "15").andExpect(status().isOk());

        putBalance(adminA, memberId, 2014, "15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_YEAR"));
        putBalance(adminA, memberId, thisYear + 2, "15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_YEAR"));
    }

    // ===== 권한 =====

    @Test
    @DisplayName("사원은 입력 API가 403, 토큰이 없으면 401이고, 같은 요청을 기업관리자가 하면 성공한다")
    void saveBalance_permission() throws Exception {
        int year = thisYear();

        putBalance(employeeA, employeeA.getId(), year, "15")
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/leave/manage/balances/{memberId}/{year}", employeeA.getId(), year)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantedDays\": 15}"))
                .andExpect(status().isUnauthorized());

        putBalance(adminA, employeeA.getId(), year, "15")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("조회 API: year가 없으면 400, 토큰이 없으면 401이고, 기업관리자도 자기 장부를 조회할 수 있다")
    void getMyBalance_validationAndPermission() throws Exception {
        mockMvc.perform(get("/api/leave/balance")
                        .header("Authorization", bearer(employeeA)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/leave/balance")
                        .param("year", String.valueOf(thisYear())))
                .andExpect(status().isUnauthorized());

        getBalance(adminA, thisYear())
                .andExpect(status().isOk());
    }

    // ===== 관리자 목록 =====

    @Test
    @DisplayName("목록: 이름순으로 나오고, 장부가 있는 사원은 지급·사용·잔여가, 없는 사원은 balance가 null이다")
    void listBalances_balanceAndNull() throws Exception {
        int year = thisYear();
        prepareListMembers(year);

        mockMvc.perform(listRequest(adminA, year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].memberName").value(Matchers.contains("Alice", "Bob", "Carol", "Zed")))
                // Alice: 장부 없음 → "미지급"
                .andExpect(jsonPath("$.content[0].balance").value(Matchers.nullValue()))
                // Bob: 지급 15 / 사용 0 / 잔여 15, 이름과 부서 이름이 서로 다른 값이라 순서 뒤바뀜을 잡는다
                .andExpect(jsonPath("$.content[1].memberName").value("Bob"))
                .andExpect(jsonPath("$.content[1].departmentName").value("개발팀"))
                .andExpect(jsonPath("$.content[1].balance.year").value(year))
                .andExpect(jsonPath("$.content[1].balance.grantedDays").value(15.0))
                .andExpect(jsonPath("$.content[1].balance.usedDays").value(0.0))
                .andExpect(jsonPath("$.content[1].balance.remainingDays").value(15.0))
                // Carol: 지급 20 / 사용 6 / 잔여 14 (지급·사용·잔여가 모두 다른 값)
                .andExpect(jsonPath("$.content[2].memberId").value(carol.getId().intValue()))
                .andExpect(jsonPath("$.content[2].balance.grantedDays").value(20.0))
                .andExpect(jsonPath("$.content[2].balance.usedDays").value(6.0))
                .andExpect(jsonPath("$.content[2].balance.remainingDays").value(14.0))
                // Zed(관리자): 장부 없음
                .andExpect(jsonPath("$.content[3].balance").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("목록: 다른 회사 사원과 그 장부는 섞이지 않고, 다른 회사 관리자는 자기 회사 사원을 본다")
    void listBalances_otherCompany() throws Exception {
        int year = thisYear();
        prepareListMembers(year);
        leaveBalanceRepository.save(LeaveBalance.create(companyB, employeeB, year, new BigDecimal("77")));
        Member adminB = createAdmin(companyB, "admin-b@test.com");

        mockMvc.perform(listRequest(adminA, year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.memberId == " + employeeB.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.balance.grantedDays == 77)]").isEmpty());

        mockMvc.perform(listRequest(adminB, year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.memberId == " + employeeB.getId() + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("목록 페이징: size=1이면 한 명씩(개수 쿼리까지), 페이지마다 그 사원의 장부가 붙고, 범위를 넘은 페이지는 빈 목록이다")
    void listBalances_paging() throws Exception {
        int year = thisYear();
        prepareListMembers(year);

        mockMvc.perform(listRequest(adminA, year).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].memberName").value("Alice"))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(4));

        mockMvc.perform(listRequest(adminA, year).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].memberName").value("Bob"))
                .andExpect(jsonPath("$.content[0].balance.grantedDays").value(15.0));

        mockMvc.perform(listRequest(adminA, year).param("size", "1").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    @DisplayName("목록: size를 101로 요청해도 최대 100명만 나온다")
    void listBalances_sizeCap() throws Exception {
        for (int i = 0; i < 101; i++) {
            createEmployee(companyA, employeeA.getDepartment(), "bulk" + i + "@test.com");
        }

        mockMvc.perform(listRequest(adminA, thisYear()).param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    @DisplayName("목록: sort 값을 보내도 무시하고 이름순을 유지한다")
    void listBalances_sortIgnored() throws Exception {
        int year = thisYear();
        prepareListMembers(year);

        mockMvc.perform(listRequest(adminA, year).param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].memberName").value(Matchers.contains("Alice", "Bob", "Carol", "Zed")));
    }

    @Test
    @DisplayName("목록: 퇴사일이 오늘인 사원은 아직 보이고, 어제 퇴사한 사원은 빠진다 (경계 쌍)")
    void listBalances_resignBoundary() throws Exception {
        int year = thisYear();
        prepareListMembers(year);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        alice.resign(today);
        carol.resign(today.minusDays(1));

        mockMvc.perform(listRequest(adminA, year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].memberName").value(Matchers.contains("Alice", "Bob", "Zed")));
    }

    @Test
    @DisplayName("목록: 사원은 403, 토큰이 없으면 401, year가 없으면 400이고, 기업관리자는 성공한다")
    void listBalances_permissionAndValidation() throws Exception {
        int year = thisYear();

        mockMvc.perform(listRequest(employeeA, year)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/leave/manage/balances").param("year", String.valueOf(year)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/leave/manage/balances").header("Authorization", bearer(adminA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(listRequest(adminA, year)).andExpect(status().isOk());
    }

    // ===== 일수 계산 =====

    @Test
    @DisplayName("일수 계산: 월~금은 5일, 하루짜리는 1일이다 (휴일이 없을 때)")
    void getDays_weekdays() throws Exception {
        getDays(employeeA, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(5.0));
        getDays(employeeA, "ANNUAL", MONDAY, MONDAY)
                .andExpect(jsonPath("$.days").value(1.0));
    }

    @Test
    @DisplayName("일수 계산: 토~일만 고르면 에러가 아니라 0일이다")
    void getDays_weekendOnlyIsZero() throws Exception {
        getDays(employeeA, "ANNUAL", SATURDAY, SUNDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(0.0));
    }

    @Test
    @DisplayName("일수 계산: 반차는 하루면 0.5일(오전·오후), 이틀이면 400 INVALID_LEAVE_PERIOD")
    void getDays_halfDay() throws Exception {
        getDays(employeeA, "HALF_AM", MONDAY, MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(0.5));
        getDays(employeeA, "HALF_PM", MONDAY, MONDAY)
                .andExpect(jsonPath("$.days").value(0.5));
        getDays(employeeA, "HALF_AM", MONDAY, TUESDAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_PERIOD"));
    }

    @Test
    @DisplayName("일수 계산: 시작일이 종료일보다 늦거나 연도가 걸치면 400 INVALID_LEAVE_PERIOD")
    void getDays_invalidPeriod() throws Exception {
        int year = thisYear();

        getDays(employeeA, "ANNUAL", TUESDAY, MONDAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_PERIOD"));
        getDays(employeeA, "ANNUAL", LocalDate.of(year, 12, 31), LocalDate.of(year + 1, 1, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVE_PERIOD"));
    }

    @Test
    @DisplayName("일수 계산: 내 회사 휴일만 빠지고 다른 회사 휴일은 섞이지 않는다")
    void getDays_companyHoliday() throws Exception {
        companyHolidayRepository.save(CompanyHoliday.create(companyA, WEDNESDAY, "A회사 휴일", false));
        companyHolidayRepository.save(CompanyHoliday.create(companyB, THURSDAY, "B회사 휴일", false));
        companyHolidayRepository.save(CompanyHoliday.create(companyB, FRIDAY, "B회사 휴일2", false));

        getDays(employeeA, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(jsonPath("$.days").value(4.0));   // 수요일만 빠짐
        getDays(employeeB, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(jsonPath("$.days").value(3.0));   // 목·금이 빠짐
    }

    @Test
    @DisplayName("일수 계산: 한국 회사는 국가 공휴일이 빠지고, 일본 회사는 한국 공휴일이 반영되지 않는다")
    void getDays_publicHoliday() throws Exception {
        publicHolidayRepository.save(PublicHoliday.create("KR", TUESDAY, "국가 공휴일"));
        Company companyJp = companyRepository.save(Company.create("JP회사", "333-33-33333", "JP", "Asia/Tokyo"));
        var deptJp = createDepartment(companyJp, null, "개발팀");
        Member employeeJp = createEmployee(companyJp, deptJp, "emp-jp@test.com");

        getDays(employeeA, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(jsonPath("$.days").value(4.0));
        getDays(employeeJp, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(jsonPath("$.days").value(5.0));
    }

    @Test
    @DisplayName("일수 계산: 국가 공휴일과 회사 휴일이 같은 날 겹쳐도 한 번만 빠진다")
    void getDays_overlappingHolidays() throws Exception {
        publicHolidayRepository.save(PublicHoliday.create("KR", WEDNESDAY, "국가 공휴일"));
        companyHolidayRepository.save(CompanyHoliday.create(companyA, WEDNESDAY, "창립기념일", false));

        getDays(employeeA, "ANNUAL", MONDAY, FRIDAY)
                .andExpect(jsonPath("$.days").value(4.0));
    }

    @Test
    @DisplayName("일수 계산: 파라미터 누락·형식 오류는 400, 토큰이 없으면 401이고, 기업관리자도 사용할 수 있다")
    void getDays_validationAndPermission() throws Exception {
        String monday = MONDAY.toString();
        String friday = FRIDAY.toString();

        getDaysRaw(employeeA, null, monday, friday).andExpect(status().isBadRequest());
        getDaysRaw(employeeA, "ANNUAL", null, friday).andExpect(status().isBadRequest());
        getDaysRaw(employeeA, "ANNUAL", monday, null).andExpect(status().isBadRequest());
        getDaysRaw(employeeA, "ABC", monday, friday).andExpect(status().isBadRequest());
        getDaysRaw(employeeA, "ANNUAL", "abc", friday).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/leave/days")
                        .param("leaveType", "ANNUAL")
                        .param("startDate", monday)
                        .param("endDate", friday))
                .andExpect(status().isUnauthorized());

        getDays(adminA, "ANNUAL", MONDAY, FRIDAY).andExpect(status().isOk());
    }

    // ===== 도우미 =====

    private ResultActions putBalance(Member actor, Long memberId, int year, String grantedDays) throws Exception {
        return putBalanceRaw(actor, memberId, year, "{\"grantedDays\": " + grantedDays + "}");
    }

    private ResultActions putBalanceRaw(Member actor, Long memberId, int year, String body) throws Exception {
        return mockMvc.perform(put("/api/leave/manage/balances/{memberId}/{year}", memberId, year)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getBalance(Member actor, int year) throws Exception {
        return mockMvc.perform(get("/api/leave/balance")
                .param("year", String.valueOf(year))
                .header("Authorization", bearer(actor)));
    }

    // 이름을 직접 정해 정렬 결과가 눈에 보이게 한다: Alice(장부 없음), Bob(15), Carol(20, 사용 6), Zed(관리자, 장부 없음)
    private void prepareListMembers(int year) {
        rename(adminA, "Zed");
        rename(employeeA, "Bob");
        alice = createEmployee(companyA, employeeA.getDepartment(), "alice@test.com");
        rename(alice, "Alice");
        carol = createEmployee(companyA, employeeA.getDepartment(), "carol@test.com");
        rename(carol, "Carol");

        leaveBalanceRepository.save(LeaveBalance.create(companyA, employeeA, year, new BigDecimal("15")));
        LeaveBalance carolBalance = leaveBalanceRepository.save(
                LeaveBalance.create(companyA, carol, year, new BigDecimal("20")));
        ReflectionTestUtils.setField(carolBalance, "usedDays", new BigDecimal("6"));
    }

    private void rename(Member member, String name) {
        member.update(name, member.getDepartment(), member.getJobGrade(), member.getJobTitle(), member.getHireDate());
    }

    private MockHttpServletRequestBuilder listRequest(Member actor, int year) {
        return get("/api/leave/manage/balances")
                .param("year", String.valueOf(year))
                .header("Authorization", bearer(actor));
    }

    private ResultActions getDays(Member actor, String leaveType, LocalDate startDate, LocalDate endDate) throws Exception {
        return getDaysRaw(actor, leaveType, startDate.toString(), endDate.toString());
    }

    // null인 파라미터는 보내지 않는다 (누락 검사용)
    private ResultActions getDaysRaw(Member actor, String leaveType, String startDate, String endDate) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/leave/days")
                .header("Authorization", bearer(actor));
        if (leaveType != null) request.param("leaveType", leaveType);
        if (startDate != null) request.param("startDate", startDate);
        if (endDate != null) request.param("endDate", endDate);
        return mockMvc.perform(request);
    }

    // 도우미로 만든 회사는 항상 Asia/Seoul이라 회사 시간대 기준 올해와 같다
    private static int thisYear() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    }
}