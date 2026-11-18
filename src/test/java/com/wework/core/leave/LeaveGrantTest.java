package com.wework.core.leave;

import com.wework.core.company.entity.Company;
import com.wework.core.company.entity.CompanyStatus;
import com.wework.core.leave.entity.LeaveBalance;
import com.wework.core.leave.entity.LeaveGrantRule;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import com.wework.core.leave.scheduler.LeaveGrantScheduler;
import com.wework.core.leave.service.LeaveGrantService;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.core.member.entity.MemberStatus;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeaveGrantTest extends IntegrationTestSupport {

    // 서비스 테스트는 날짜를 직접 넘겨서 시계에 흔들리지 않게 한다
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final int YEAR = 2026;

    @Autowired private LeaveGrantService leaveGrantService;
    @Autowired private LeaveGrantScheduler leaveGrantScheduler;
    @Autowired private LeaveGrantRuleRepository leaveGrantRuleRepository;
    @Autowired private LeaveBalanceRepository leaveBalanceRepository;

    private Company companyA;
    private Company companyB;

    private Member m6;                 // 2020 입사 → 6년차 → 16일
    private Member m1;                 // 2025-12-31 입사 → 1년차 → 15일
    private Member m0;                 // 2026 입사 → 0년차 (첫해, 제외)
    private Member mNoHire;            // 입사일 없음 (제외)
    private Member mInvited;           // 가입 전 (제외)
    private Member mResignedYesterday; // 어제 퇴사 (제외)
    private Member mResignedToday;     // 오늘 퇴사 (포함, 6년차 → 16일)
    private Member mB;                 // 회사 B 사원 (A를 돌릴 때 건드리면 안 됨)

    @BeforeEach
    void prepareData() {
        companyA = createCompany("지급A", "9980000001");
        companyB = createCompany("지급B", "9980000002");

        saveRule(companyA, 1, "15");
        saveRule(companyA, 3, "16");
        saveRule(companyB, 1, "20");

        m6 = employee(companyA, "g-6@test.com", LocalDate.of(2020, 3, 2));
        m1 = employee(companyA, "g-1@test.com", LocalDate.of(2025, 12, 31));
        m0 = employee(companyA, "g-0@test.com", LocalDate.of(2026, 2, 3));
        mNoHire = employee(companyA, "g-nohire@test.com", null);

        mInvited = employee(companyA, "g-invited@test.com", LocalDate.of(2020, 3, 2));
        ReflectionTestUtils.setField(mInvited, "status", MemberStatus.INVITED);

        mResignedYesterday = employee(companyA, "g-resign-y@test.com", LocalDate.of(2020, 3, 2));
        resign(mResignedYesterday, TODAY.minusDays(1));

        mResignedToday = employee(companyA, "g-resign-t@test.com", LocalDate.of(2020, 3, 2));
        resign(mResignedToday, TODAY);

        mB = employee(companyB, "g-b@test.com", LocalDate.of(2020, 3, 2));
    }

    // ---------- 도우미 ----------

    private Member employee(Company company, String email, LocalDate hireDate) {
        Member member = createMember(company, null, email, MemberRole.EMPLOYEE);
        ReflectionTestUtils.setField(member, "hireDate", hireDate);
        return member;
    }

    private void resign(Member member, LocalDate resignDate) {
        ReflectionTestUtils.setField(member, "status", MemberStatus.RESIGNED);
        ReflectionTestUtils.setField(member, "resignDate", resignDate);
    }

    private void saveRule(Company company, int serviceYear, String days) {
        leaveGrantRuleRepository.save(LeaveGrantRule.create(company, serviceYear, new BigDecimal(days)));
    }

    private int run(Company company) {
        return leaveGrantService.grantForCompany(company.getId(), YEAR, TODAY);
    }

    // 장부가 없으면 null (Optional을 쓰는 곳은 여기 한 곳)
    private LeaveBalance findBalance(Company company, Member member, int year) {
        return leaveBalanceRepository
                .findByCompanyIdAndMemberIdAndYear(company.getId(), member.getId(), year)
                .orElse(null);
    }

    private void assertBalance(String expectedDays, LeaveBalance balance) {
        assertNotNull(balance, "장부가 있어야 한다");
        assertEquals(0, new BigDecimal(expectedDays).compareTo(balance.getGrantedDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(balance.getUsedDays()));
    }

    // ================= 서비스 =================

    @Test
    @DisplayName("재직 중이고 1년차 이상인 사원에게 설정 표대로 지급한다 (사용 일수는 0)")
    void grantsEligibleMembers() {
        int granted = run(companyA);

        assertEquals(3, granted);
        assertBalance("16", findBalance(companyA, m6, YEAR));
        assertBalance("15", findBalance(companyA, m1, YEAR));
        assertBalance("16", findBalance(companyA, mResignedToday, YEAR));
    }

    @Test
    @DisplayName("입사 첫해 사원은 지급하지 않는다")
    void skipsFirstYearMember() {
        run(companyA);
        assertNull(findBalance(companyA, m0, YEAR));
    }

    @Test
    @DisplayName("입사일이 없는 사원은 지급하지 않는다")
    void skipsMemberWithoutHireDate() {
        run(companyA);
        assertNull(findBalance(companyA, mNoHire, YEAR));
    }

    @Test
    @DisplayName("가입 전(INVITED) 사원은 지급하지 않는다")
    void skipsInvitedMember() {
        run(companyA);
        assertNull(findBalance(companyA, mInvited, YEAR));
    }

    @Test
    @DisplayName("어제 퇴사한 사원은 제외, 오늘 퇴사한 사원은 포함 (경계 쌍)")
    void resignedBoundary() {
        run(companyA);
        assertNull(findBalance(companyA, mResignedYesterday, YEAR));
        assertNotNull(findBalance(companyA, mResignedToday, YEAR));
    }

    @Test
    @DisplayName("두 번 돌려도 결과가 같다 (두 번째는 0명, 기존 장부 그대로)")
    void secondRunGrantsNothing() {
        run(companyA);
        Long firstId = findBalance(companyA, m6, YEAR).getId();

        int second = run(companyA);

        assertEquals(0, second);
        assertEquals(firstId, findBalance(companyA, m6, YEAR).getId());
        assertBalance("16", findBalance(companyA, m6, YEAR));
    }

    @Test
    @DisplayName("관리자가 이미 입력한 장부는 건드리지 않는다")
    void keepsAdminEnteredBalance() {
        leaveBalanceRepository.save(LeaveBalance.create(companyA, m6, YEAR, new BigDecimal("20")));

        int granted = run(companyA);

        assertEquals(2, granted); // m1, mResignedToday만
        assertBalance("20", findBalance(companyA, m6, YEAR)); // 설정 표는 16이지만 20 그대로
    }

    @Test
    @DisplayName("나중에 재직이 된 사원은 다음 실행에서 보충된다")
    void nextRunPicksUpNewMember() {
        run(companyA);
        Member late = employee(companyA, "g-late@test.com", LocalDate.of(2019, 5, 1));

        int second = run(companyA);

        assertEquals(1, second);
        assertBalance("16", findBalance(companyA, late, YEAR));
    }

    @Test
    @DisplayName("설정 표가 비어 있는 회사는 아무도 지급하지 않는다")
    void emptyRulesGrantNothing() {
        Company companyC = createCompany("지급C", "9980000003");
        Member member = employee(companyC, "g-c@test.com", LocalDate.of(2020, 3, 2));

        int granted = run(companyC);

        assertEquals(0, granted);
        assertNull(findBalance(companyC, member, YEAR));
    }

    @Test
    @DisplayName("설정 표에 내 근속연수 이하의 줄이 없으면 건너뛴다")
    void noMatchingRuleSkipped() {
        Company companyD = createCompany("지급D", "9980000004");
        saveRule(companyD, 3, "16");
        Member oneYear = employee(companyD, "g-d1@test.com", LocalDate.of(2025, 12, 31));
        Member threeYears = employee(companyD, "g-d3@test.com", LocalDate.of(2023, 6, 1));

        int granted = run(companyD);

        assertEquals(1, granted);
        assertNull(findBalance(companyD, oneYear, YEAR));
        assertBalance("16", findBalance(companyD, threeYears, YEAR));
    }

    @Test
    @DisplayName("회사끼리 섞이지 않는다 (A를 돌려도 B는 그대로, 각자의 설정 표를 씀)")
    void companiesAreIsolated() {
        run(companyA);
        assertNull(findBalance(companyB, mB, YEAR));

        assertEquals(1, run(companyB));
        assertBalance("20", findBalance(companyB, mB, YEAR));
        assertBalance("16", findBalance(companyA, m6, YEAR));
    }

    @Test
    @DisplayName("근속연수와 장부의 연도는 grantYear를 따르고, 재직 판단만 today를 따른다")
    void grantYearDrivesServiceYearsAndLedgerYear() {
        int granted = leaveGrantService.grantForCompany(companyA.getId(), 2027, TODAY);

        assertEquals(4, granted); // m6, m1, m0, mResignedToday
        assertBalance("16", findBalance(companyA, m6, 2027)); // 7년차
        assertBalance("15", findBalance(companyA, m1, 2027)); // 2년차
        assertBalance("15", findBalance(companyA, m0, 2027)); // 2026 입사는 2027에 1년차
        assertNull(findBalance(companyA, m6, YEAR));          // 2026 장부는 만들지 않음
    }

    @Test
    @DisplayName("지급 일수가 0인 줄은 0일 장부를 만든다 (지급 안 함과 다름)")
    void zeroDaysRuleCreatesZeroBalance() {
        Company companyE = createCompany("지급E", "9980000005");
        saveRule(companyE, 1, "0");
        Member member = employee(companyE, "g-e@test.com", LocalDate.of(2020, 3, 2));

        assertEquals(1, run(companyE));
        assertBalance("0", findBalance(companyE, member, YEAR));
    }

    // ================= 스케줄러 (실제 시계 사용 → 입사일 2000-01-01로 어느 해든 대상) =================

    private static int currentYear() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    }

    @Test
    @DisplayName("스케줄러는 KR이고 ACTIVE인 회사만 지급한다 (JP, INACTIVE는 제외)")
    void schedulerGrantsOnlyKrActiveCompanies() {
        Company kr = createCompany("지급KR", "9980000010");
        Company jp = companyRepository.save(Company.create("지급JP", "9980000011", "JP", "Asia/Tokyo"));
        Company inactive = createCompany("지급INACTIVE", "9980000012");
        ReflectionTestUtils.setField(inactive, "status", CompanyStatus.INACTIVE);

        saveRule(kr, 1, "15");
        saveRule(jp, 1, "15");
        saveRule(inactive, 1, "15");
        Member krMember = employee(kr, "s-kr@test.com", LocalDate.of(2000, 1, 1));
        Member jpMember = employee(jp, "s-jp@test.com", LocalDate.of(2000, 1, 1));
        Member inactiveMember = employee(inactive, "s-inactive@test.com", LocalDate.of(2000, 1, 1));

        leaveGrantScheduler.grantAnnualLeave();

        assertBalance("15", findBalance(kr, krMember, currentYear()));
        assertNull(findBalance(jp, jpMember, currentYear()));
        assertNull(findBalance(inactive, inactiveMember, currentYear()));
    }

    @Test
    @DisplayName("시간대가 이상한 회사가 있어도 다음 회사는 계속 지급한다")
    void schedulerKeepsGoingAfterBrokenCompany() {
        // 이상한 회사를 먼저 저장해서 반복문에서 정상 회사보다 앞에 오게 한다
        companyRepository.save(Company.create("지급BAD", "9980000020", "KR", "Foo/Bar"));
        Company good = createCompany("지급GOOD", "9980000021");
        saveRule(good, 1, "15");
        Member goodMember = employee(good, "s-good@test.com", LocalDate.of(2000, 1, 1));

        assertDoesNotThrow(() -> leaveGrantScheduler.grantAnnualLeave());

        assertBalance("15", findBalance(good, goodMember, currentYear()));
    }

    @Test
    @DisplayName("스케줄러를 두 번 돌려도 결과가 같다")
    void schedulerRunTwiceSameResult() {
        Company kr = createCompany("지급TWICE", "9980000030");
        saveRule(kr, 1, "15");
        Member member = employee(kr, "s-twice@test.com", LocalDate.of(2000, 1, 1));

        leaveGrantScheduler.grantAnnualLeave();
        Long firstId = findBalance(kr, member, currentYear()).getId();
        leaveGrantScheduler.grantAnnualLeave();

        LeaveBalance after = findBalance(kr, member, currentYear());
        assertEquals(firstId, after.getId());
        assertBalance("15", after);
    }
}