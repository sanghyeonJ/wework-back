package com.wework.core.holiday.client;

import com.wework.core.holiday.error.HolidayErrorCode;
import com.wework.global.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PublicHolidayClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public PublicHolidayClient(
            @Value("${app.holiday-api.base-url}") String baseUrl,
            @Value("${app.holiday-api.service-key}") String serviceKey,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
    }

    // 한 해 전체(1~12월). 한 달이라도 실패하면 예외 → 반쯤 채워진 결과를 돌려주지 않음
    public List<PublicHolidayItem> fetchYear(int year) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException(HolidayErrorCode.HOLIDAY_API_KEY_NOT_CONFIGURED);
        }

        List<PublicHolidayItem> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            result.addAll(fetchMonth(year, month));
        }
        return result;
    }

    // 한 달 호출
    private List<PublicHolidayItem> fetchMonth(int year, int month) {
        try{
            // 1) 호출해서 응답을 글자로 받기
            String json = restClient.get()
                    .uri("/getRestDeInfo?solYear={year}&solMonth={month}&ServiceKey={key}&_type=json",
                            year, String.format("%02d", month), serviceKey)
                    .retrieve()
                    .body(String.class);
            if (json == null) {
                throw new BusinessException(HolidayErrorCode.HOLIDAY_API_FAILED);
            }

            // 2) 글자 → JSON 노드
            JsonNode root = objectMapper.readTree(json);

            // 3) 결과 코드 확인
            String resultCode = root.path("response").path("header").path("resultCode").asString();
            if (!resultCode.equals("00")){
                log.warn("공휴일 API 오류 year={}, month={}, resultCode={}", year, String.format("%02d", month), resultCode);
                throw new BusinessException(HolidayErrorCode.HOLIDAY_API_FAILED);
            }

            // 4) 휴일 목록 만들기
            return parseItems(root.path("response").path("body"));
        } catch (RestClientException | JacksonException e) {
            log.warn("공휴일 API 호출 실패 year={}, month={}, 원인={}", year, month, e.getClass().getSimpleName());
            throw new BusinessException(HolidayErrorCode.HOLIDAY_API_FAILED);
        }
    }

    // 응답 JSON에서 휴일 목록 꺼내기
    static List<PublicHolidayItem> parseItems(JsonNode body) {
        List<PublicHolidayItem> result = new ArrayList<>();

        // 1) 0개일 때: items가 객체가 아니라 "" (문자열)
        JsonNode items = body.path("items");
        if (!items.isObject()){
            return result;
        }

        // 2) 1개일 때는 객체, 2개 이상일 때는 배열
        JsonNode item = items.path("item");
        if (item.isArray()) {
            for (JsonNode node : item) {
                addIfHoliday(result, node);
            }
        } else if (item.isObject()) {
            addIfHoliday(result, item);
        }

        return result;
    }

    private static void addIfHoliday(List<PublicHolidayItem> result, JsonNode node) {
        // 3) 휴일(Y)이 아니면 건너뜀
        if (!node.path("isHoliday").asString().equals("Y")) {
            return;
        }

        // 4) 날짜: 20260101 같은 숫자 → LocalDate
        int locdate = node.path("locdate").asInt();
        LocalDate date = LocalDate.parse(String.valueOf(locdate), DateTimeFormatter.BASIC_ISO_DATE);

        // 5) 이름 정리
        String name = node.path("dateName").asString().trim();

        result.add(PublicHolidayItem.of(date, name));
    }
}
