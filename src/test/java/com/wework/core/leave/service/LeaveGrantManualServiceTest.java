package com.wework.core.leave.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.leave.dto.LeaveGrantResultResponse;
import com.wework.core.leave.entity.LeaveGrantRule;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.core.leave.repository.LeaveBalanceRepository;
import com.wework.core.leave.repository.LeaveGrantRuleRepository;
import com.wework.core.member.entity.MemberStatus;
import com.wework.core.member.repository.MemberRepository;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import com.wework.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeaveGrantManualServiceTest {

    private static final Long COMPANY_ID = 1L;

    private LeaveGrantRuleRepository leaveGrantRuleRepository;
    private MemberRepository memberRepository;
    private LeaveBalanceRepository leaveBalanceRepository;
    private CompanyRepository companyRepository;
    private LeaveGrantService service;
    private Company company;

    @BeforeEach
    void setUp() {
        leaveGrantRuleRepository = mock(LeaveGrantRuleRepository.class);
        memberRepository = mock(MemberRepository.class);
        leaveBalanceRepository = mock(LeaveBalanceRepository.class);
        companyRepository = mock(CompanyRepository.class);
        service = new LeaveGrantService(
                leaveGrantRuleRepository, memberRepository, leaveBalanceRepository, companyRepository);

        company = Company.create("수동지급단위", "9961111111", "KR", "Asia/Seoul");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    }

    // ---------- 도우미 ----------

    // 서비스가 회사 시간대(서울)로 구하는 "올해"와 같은 기준
    private static int thisYear() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    }

    private void givenRules() {
        when(leaveGrantRuleRepository.findAllByCompanyIdOrderByServiceYearAsc(COMPANY_ID))
                .thenReturn(List.of(LeaveGrantRule.create(company, 1, new BigDecimal("15"))));
    }

    private void assertErrorCode(ErrorCode expected, int year) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.grantManually(COMPANY_ID, year));
        assertEquals(expected, ex.getErrorCode());
    }

    // ================= 테스트 =================

    @Test
    @DisplayName("연도가 범위를 벗어나면 INVALID_LEAVE_YEAR이고 설정 표·사원·장부는 건드리지 않는다")
    void invalidYearStopsBeforeAnythingElse() {
        assertErrorCode(LeaveErrorCode.INVALID_LEAVE_YEAR, 2014);

        verifyNoInteractions(leaveGrantRuleRepository, memberRepository, leaveBalanceRepository);
    }

    @Test
    @DisplayName("연도 경계: 2014·올해+2는 거절, 2015·올해+1은 연도 검사를 통과한다 (쌍)")
    void yearBoundaries() {
        // 설정 표가 비어 있으므로, 연도 검사를 통과하면 그 다음 검사인 409에 걸린다
        assertErrorCode(LeaveErrorCode.INVALID_LEAVE_YEAR, 2014);
        assertErrorCode(LeaveErrorCode.LEAVE_GRANT_RULES_NOT_SET, 2015);
        assertErrorCode(LeaveErrorCode.LEAVE_GRANT_RULES_NOT_SET, thisYear() + 1);
        assertErrorCode(LeaveErrorCode.INVALID_LEAVE_YEAR, thisYear() + 2);
    }

    @Test
    @DisplayName("설정 표가 비어 있으면 LEAVE_GRANT_RULES_NOT_SET이고 사원 조회·장부 저장을 하지 않는다")
    void emptyRulesStopsBeforeGranting() {
        assertErrorCode(LeaveErrorCode.LEAVE_GRANT_RULES_NOT_SET, thisYear());

        verifyNoInteractions(memberRepository, leaveBalanceRepository);
    }

    @Test
    @DisplayName("회사를 찾지 못하면 INTERNAL_SERVER_ERROR (토큰의 회사라 정상 흐름에서는 없음)")
    void companyNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.grantManually(999L, thisYear()));

        assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("정상이면 요청한 연도와 지급 수를 돌려주고 재직 사원을 조회한다")
    void normalReturnsYearAndCount() {
        givenRules();
        // 사원 조회는 따로 지정하지 않으면 빈 목록 → 새로 지급할 사람이 없어 0명

        LeaveGrantResultResponse result = service.grantManually(COMPANY_ID, thisYear());

        assertEquals(thisYear(), result.getYear());
        assertEquals(0, result.getGrantedCount());
        verify(memberRepository).findEmployedWithHireDate(
                eq(COMPANY_ID), any(LocalDate.class), eq(MemberStatus.ACTIVE), eq(MemberStatus.RESIGNED));
        verifyNoInteractions(leaveBalanceRepository);
    }
}