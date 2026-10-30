package com.wework.core.holiday;

import com.wework.core.holiday.client.PublicHolidayClient;
import com.wework.core.holiday.client.PublicHolidayItem;
import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.entity.PublicHoliday;
import com.wework.core.holiday.error.HolidayErrorCode;
import com.wework.core.holiday.repository.PublicHolidayRepository;
import com.wework.core.holiday.service.PublicHolidaySyncService;
import com.wework.core.member.entity.Member;
import com.wework.global.error.BusinessException;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicHolidaySyncServiceTest extends IntegrationTestSupport {

    // 실제 공공 API 대신 가짜로 교체: 테스트가 원하는 목록을 돌려줌
    @MockitoBean
    PublicHolidayClient publicHolidayClient;

    @Autowired
    PublicHolidaySyncService publicHolidaySyncService;

    @Autowired
    PublicHolidayRepository publicHolidayRepository;

    int year;

    @BeforeEach
    void setUp() {
        year = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
    }

    private PublicHolidayItem item(int month, int day, String name) {
        return PublicHolidayItem.of(LocalDate.of(year, month, day), name);
    }

    private List<PublicHoliday> saved() {
        return publicHolidayRepository
                .findAllByCountryCodeAndHolidayDateBetweenOrderByHolidayDateAscIdAsc(
                        "KR", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Test
    void 처음_불러오면_전부_저장한다() {
        when(publicHolidayClient.fetchYear(year)).thenReturn(
                List.of(item(1, 1, "1월1일"), item(5, 1, "노동절"), item(5, 5, "어린이날")));

        PublicHolidaySyncResponse response = publicHolidaySyncService.sync("KR", year);

        assertEquals(3, response.getFetchedCount());
        assertEquals(3, response.getAddedCount());
        assertEquals(3, saved().size());
    }

    @Test
    void 다시_불러오면_추가는_0이다() {
        when(publicHolidayClient.fetchYear(year)).thenReturn(
                List.of(item(1, 1, "1월1일"), item(5, 1, "노동절")));
        publicHolidaySyncService.sync("KR", year);

        PublicHolidaySyncResponse second = publicHolidaySyncService.sync("KR", year);

        assertEquals(2, second.getFetchedCount());
        assertEquals(0, second.getAddedCount());
        assertEquals(2, saved().size());
    }

    @Test
    void 새로_생긴_것만_추가한다() {
        // 첫 번째 호출은 2개, 두 번째 호출은 대체공휴일이 공포되어 3개
        when(publicHolidayClient.fetchYear(year))
                .thenReturn(List.of(item(5, 24, "부처님오신날"), item(5, 1, "노동절")))
                .thenReturn(List.of(item(5, 24, "부처님오신날"), item(5, 1, "노동절"),
                        item(5, 25, "대체공휴일(부처님오신날)")));
        publicHolidaySyncService.sync("KR", year);

        PublicHolidaySyncResponse second = publicHolidaySyncService.sync("KR", year);

        assertEquals(3, second.getFetchedCount());
        assertEquals(1, second.getAddedCount());
        assertEquals(3, saved().size());
    }

    @Test
    void 같은_날짜에_이름이_다른_휴일은_둘_다_저장한다() {
        when(publicHolidayClient.fetchYear(year)).thenReturn(
                List.of(item(5, 5, "어린이날"), item(5, 5, "대체공휴일(어린이날)")));

        PublicHolidaySyncResponse response = publicHolidaySyncService.sync("KR", year);

        assertEquals(2, response.getAddedCount());
        assertEquals(2, saved().size());
    }

    @Test
    void API가_같은_항목을_두_번_줘도_한_번만_저장한다() {
        when(publicHolidayClient.fetchYear(year)).thenReturn(
                List.of(item(5, 1, "노동절"), item(5, 1, "노동절")));

        PublicHolidaySyncResponse response = publicHolidaySyncService.sync("KR", year);

        assertEquals(2, response.getFetchedCount());
        assertEquals(1, response.getAddedCount());
        assertEquals(1, saved().size());
    }

    @Test
    void API가_실패하면_아무것도_저장하지_않는다() {
        // 첫 호출은 실패, 두 번째 호출은 성공 (문제를 뺀 요청이 성공하는지 확인)
        when(publicHolidayClient.fetchYear(year))
                .thenThrow(new BusinessException(HolidayErrorCode.HOLIDAY_API_FAILED))
                .thenReturn(List.of(item(5, 1, "노동절")));

        assertThrows(BusinessException.class, () -> publicHolidaySyncService.sync("KR", year));
        assertEquals(0, saved().size());

        PublicHolidaySyncResponse retry = publicHolidaySyncService.sync("KR", year);
        assertEquals(1, retry.getAddedCount());
    }

    @Test
    void 지원하지_않는_국가는_API를_부르기_전에_거절한다() {
        assertThrows(BusinessException.class, () -> publicHolidaySyncService.sync("JP", year));
        verify(publicHolidayClient, never()).fetchYear(anyInt());

        // 국가만 바꾼 요청은 성공
        when(publicHolidayClient.fetchYear(year)).thenReturn(List.of(item(5, 1, "노동절")));
        assertEquals(1, publicHolidaySyncService.sync("KR", year).getAddedCount());
    }

    @Test
    void 연도_범위_경계를_앞뒤_쌍으로_확인한다() {
        int maxYear = year + 1;

        // 바로 밖: 2014, 내후년 → 거절, API는 호출하지 않음
        assertThrows(BusinessException.class, () -> publicHolidaySyncService.sync("KR", 2014));
        assertThrows(BusinessException.class, () -> publicHolidaySyncService.sync("KR", maxYear + 1));
        verify(publicHolidayClient, never()).fetchYear(anyInt());

        // 바로 안: 2015, 내년 → 통과
        when(publicHolidayClient.fetchYear(anyInt())).thenReturn(List.of());
        assertDoesNotThrow(() -> publicHolidaySyncService.sync("KR", 2015));
        assertDoesNotThrow(() -> publicHolidaySyncService.sync("KR", maxYear));
    }

    @Test
    void 국가_코드는_공백과_대소문자를_정리해서_저장한다() {
        when(publicHolidayClient.fetchYear(year)).thenReturn(List.of(item(5, 1, "노동절")));

        PublicHolidaySyncResponse response = publicHolidaySyncService.sync(" kr ", year);

        assertEquals("KR", response.getCountryCode());
        assertEquals(1, saved().size());
    }

    @Test
    void 기업관리자와_토큰_없는_요청은_막힌다() throws Exception {
        Member seoulAdmin = createAdmin(createCompany("위워크 서울", "111"), "admin@seoul.test");

        // 기업관리자: 최고관리자 API는 403
        mockMvc.perform(post("/api/admin/public-holidays/sync")
                        .param("countryCode", "KR")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/public-holidays")
                        .param("countryCode", "KR")
                        .param("year", String.valueOf(year))
                        .header("Authorization", bearer(seoulAdmin)))
                .andExpect(status().isForbidden());

        // 토큰 없음: 401
        mockMvc.perform(get("/api/admin/public-holidays")
                        .param("countryCode", "KR")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}