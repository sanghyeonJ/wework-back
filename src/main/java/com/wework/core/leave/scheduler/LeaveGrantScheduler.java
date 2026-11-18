package com.wework.core.leave.scheduler;

import com.wework.core.company.entity.Company;
import com.wework.core.company.entity.CompanyStatus;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.service.LeaveGrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LeaveGrantScheduler {

    private static final String AUTO_GRANT_COUNTRY_CODE = "KR";

    private final CompanyRepository companyRepository;
    private final LeaveGrantService leaveGrantService;

    @Scheduled(cron = "0 0 * * * *")
    public void grantAnnualLeave() {
        List<Company> companies = companyRepository.findAllByCountryCodeAndStatus(AUTO_GRANT_COUNTRY_CODE, CompanyStatus.ACTIVE);

        for (Company company : companies) {
            try {
                LocalDate today = LocalDate.now(ZoneId.of(company.getTimeZone()));
                int granted = leaveGrantService.grantForCompany(company.getId(), today.getYear(), today);
                if (granted > 0) {
                    log.info("연차 자동 지급: companyId={}, 지급 {}명", company.getId(), granted);
                }
            } catch (Exception e) {
                log.error("연차 자동 지급 실패: companyId={}", company.getId(), e);
            }
        }
    }

}
