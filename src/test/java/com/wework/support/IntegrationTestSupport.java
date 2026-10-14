package com.wework.support;

import com.wework.core.company.Company;
import com.wework.core.company.CompanyRepository;
import com.wework.core.department.Department;
import com.wework.core.department.DepartmentRepository;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberRole;
import com.wework.core.position.JobGrade;
import com.wework.core.position.JobGradeRepository;
import com.wework.core.position.JobTitle;
import com.wework.core.position.JobTitleRepository;
import com.wework.global.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    protected static final String TEST_PASSWORD = "password1234!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CompanyRepository companyRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected DepartmentRepository departmentRepository;
    @Autowired
    protected JobGradeRepository jobGradeRepository;
    @Autowired
    protected JobTitleRepository jobTitleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtProvider jwtProvider;

    // ===== 데이터 만들기 =====

    protected Company createCompany(String name, String businessNumber) {
        return companyRepository.save(Company.create(name, businessNumber, "KR", "Asia/Seoul"));
    }

    // ACTIVE 회원 (비밀번호는 TEST_PASSWORD)
    protected Member createMember(Company company, Department department, String email, MemberRole role) {
        Member member = Member.builder()
                .company(company)
                .department(department)
                .email(email)
                .name(email.substring(0, email.indexOf('@')))
                .role(role)
                .build();
        member.activate(passwordEncoder.encode(TEST_PASSWORD));
        return memberRepository.save(member);
    }

    protected Member createAdmin(Company company, String email) {
        return createMember(company, null, email, MemberRole.COMPANY_ADMIN);
    }

    protected Member createEmployee(Company company, Department department, String email) {
        return createMember(company, department, email, MemberRole.EMPLOYEE);
    }

    protected Department createDepartment(Company company, Department parent, String name) {
        return departmentRepository.save(Department.create(company, parent, name));
    }

    protected JobGrade createJobGrade(Company company, String name, int level) {
        return jobGradeRepository.save(JobGrade.create(company, name, level));
    }

    protected JobTitle createJobTitle(Company company, String name, int level) {
        return jobTitleRepository.save(JobTitle.create(company, name, level));
    }

    // ===== 토큰 =====

    // Authorization 헤더 값 ("Bearer " + 액세스 토큰)
    protected String bearer(Member member) {
        Long companyId = (member.getCompany() != null) ? member.getCompany().getId() : null;
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), companyId, member.getRole());
    }

}
