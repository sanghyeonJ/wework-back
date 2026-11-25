package com.wework.core.member;

import com.wework.core.company.entity.Company;
import com.wework.core.department.entity.Department;
import com.wework.core.member.entity.Member;
import com.wework.core.member.entity.MemberRole;
import com.wework.core.position.entity.JobGrade;
import com.wework.core.position.entity.JobTitle;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("결재자 후보 조회 API")
class ApproverCandidateApiTest extends IntegrationTestSupport {

    private static final String URL = "/api/members/approver-candidates";
    private static final ZoneId COMPANY_ZONE = ZoneId.of("Asia/Seoul");

    private Company company;
    private Member admin;
    private Member me;
    private Member other;

    // 이름은 이메일 앞부분: admin, me, other (후보는 호출한 사람을 뺀 나머지)
    @BeforeEach
    void setUp() {
        company = createCompany("위워크 서울", "111");
        admin = createAdmin(company, "admin@test.com");
        me = createEmployee(company, null, "me@test.com");
        other = createEmployee(company, null, "other@test.com");
    }

    // ===== 권한 =====

    @Test
    @DisplayName("사원도 호출할 수 있다")
    void employeeCanCall() throws Exception {
        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    @Test
    @DisplayName("기업관리자도 호출할 수 있다")
    void adminCanCall() throws Exception {
        asMember(admin, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("me", "other")));
    }

    @Test
    @DisplayName("최고관리자는 호출할 수 없다 (403)")
    void superAdminForbidden() throws Exception {
        Member superAdmin = createMember(null, null, "super@test.com", MemberRole.SUPER_ADMIN);

        asMember(superAdmin, get(URL))
                .andExpect(status().isForbidden())
                .andExpect(errorCode("FORBIDDEN"));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void noToken() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("UNAUTHORIZED"));
    }

    // ===== 대상 규칙 =====

    @Test
    @DisplayName("나는 목록에 없고 다른 재직 사원은 있다")
    void excludesMe() throws Exception {
        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    @Test
    @DisplayName("초대만 된(INVITED) 사원은 목록에 없다")
    void excludesInvited() throws Exception {
        createInvited(company, "invited@test.com");

        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    @Test
    @DisplayName("퇴사자는 목록에 없다")
    void excludesResigned() throws Exception {
        Member resigned = createEmployee(company, null, "resigned@test.com");
        resigned.resign(LocalDate.now(COMPANY_ZONE).minusDays(1));

        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    @Test
    @DisplayName("다른 회사 사원은 목록에 없다")
    void excludesOtherCompany() throws Exception {
        Company otherCompany = createCompany("위워크 도쿄", "222");
        createEmployee(otherCompany, null, "stranger@test.com");

        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    // ===== 정렬 · 검색 · 개수 =====

    @Test
    @DisplayName("이름 오름차순으로 정렬된다 (만든 순서와 다르게 만들어 확인)")
    void sortedByName() throws Exception {
        createEmployee(company, null, "ccc@test.com");
        createEmployee(company, null, "aaa@test.com");
        createEmployee(company, null, "bbb@test.com");

        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("aaa", "admin", "bbb", "ccc", "other")));
    }

    @Test
    @DisplayName("이름의 일부만 입력해도 검색된다")
    void searchByPartOfName() throws Exception {
        other.update("김철수", null, null, null, null);

        asMember(me, get(URL).param("keyword", "철수"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other.getId()))
                .andExpect(jsonPath("$[0].name").value("김철수"));
    }

    @Test
    @DisplayName("검색은 대소문자를 구분하지 않는다")
    void searchIgnoringCase() throws Exception {
        asMember(me, get(URL).param("keyword", "OTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("other"));
    }

    @Test
    @DisplayName("없는 이름으로 검색하면 빈 목록이다")
    void searchNoMatch() throws Exception {
        asMember(me, get(URL).param("keyword", "박영희"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("공백만 입력하면 전체 목록이다")
    void blankKeywordMeansAll() throws Exception {
        asMember(me, get(URL).param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("admin", "other")));
    }

    @Test
    @DisplayName("후보가 20명보다 많아도 이름순으로 20명만 나온다")
    void limitedToTwenty() throws Exception {
        for (int i = 1; i <= 21; i++) {
            createEmployee(company, null, "user%02d@test.com".formatted(i));
        }

        // 후보 23명(admin, other, user01~user21) 중 이름순 앞 20명 → 마지막은 user18
        asMember(me, get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[19].name").value("user18"));
    }

    // ===== 응답 모양 =====

    @Test
    @DisplayName("이름·부서·직급·직책은 있고 이메일·권한·입사일은 없다")
    void responseFields() throws Exception {
        Department team = createDepartment(company, null, "개발팀");
        JobGrade grade = createJobGrade(company, "과장", 20);
        JobTitle title = createJobTitle(company, "팀장", 10);
        other.update(other.getName(), team, grade, title, null);

        asMember(me, get(URL).param("keyword", "other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other.getId()))
                .andExpect(jsonPath("$[0].name").value("other"))
                .andExpect(jsonPath("$[0].departmentName").value("개발팀"))
                .andExpect(jsonPath("$[0].jobGradeName").value("과장"))
                .andExpect(jsonPath("$[0].jobTitleName").value("팀장"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist())
                .andExpect(jsonPath("$[0].hireDate").doesNotExist());
    }

    @Test
    @DisplayName("부서·직급·직책이 없는 사원도 목록에 있다")
    void memberWithoutDepartmentIsListed() throws Exception {
        asMember(me, get(URL).param("keyword", "other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departmentName").value(nullValue()))
                .andExpect(jsonPath("$[0].jobGradeName").value(nullValue()))
                .andExpect(jsonPath("$[0].jobTitleName").value(nullValue()));
    }

    // ===== 도우미 =====

    // 초대만 된 사원 (activate를 부르지 않으면 INVITED)
    private Member createInvited(Company company, String email) {
        return memberRepository.save(Member.builder()
                .company(company)
                .email(email)
                .name(email.substring(0, email.indexOf('@')))
                .build());
    }

    private ResultActions asMember(Member member, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(member)));
    }

    private static ResultMatcher errorCode(String code) {
        return jsonPath("$.code").value(code);
    }
}