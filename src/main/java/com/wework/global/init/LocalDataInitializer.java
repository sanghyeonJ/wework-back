package com.wework.global.init;

import com.wework.core.company.Company;
import com.wework.core.company.CompanyRepository;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private static final String TEST_PASSWORD = "password1234!";

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private Company getOrCreateCompany(String name, String countryCode, String timeZone) {
        return companyRepository.findByName(name)
                .orElseGet(() -> companyRepository.save(Company.create(name, countryCode, timeZone)));
    }
    private void createMemberIfAbsent(Company company, String email, String name, MemberRole role) {
        if (memberRepository.existsByEmail(email)) {
            return;
        }

        Member member = Member.builder()
                .company(company)
                .email(email)
                .name(name)
                .role(role)
                .build();
        member.activate(passwordEncoder.encode(TEST_PASSWORD));
        memberRepository.save(member);

        log.info("[local] 테스트 계정 생성: {} ({})", email, role);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Company seoul = getOrCreateCompany("위워크 서울", "KR", "Asia/Seoul");
        Company tokyo = getOrCreateCompany("위워크 도쿄", "JP", "Asia/Tokyo");

        createMemberIfAbsent(null, "super@wework.test", "최고관리자", MemberRole.SUPER_ADMIN);
        createMemberIfAbsent(seoul, "admin@seoul.test", "서울관리자", MemberRole.COMPANY_ADMIN);
        createMemberIfAbsent(tokyo, "admin@tokyo.test", "도쿄관리자", MemberRole.COMPANY_ADMIN);
    }

}
