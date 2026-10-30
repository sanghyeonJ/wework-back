package com.wework.core.holiday.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicHolidayClientParseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    // 실제 응답처럼 response.body 노드를 꺼내서 parseItems에 넘김
    private List<PublicHolidayItem> parse(String json) {
        JsonNode body = mapper.readTree(json).path("response").path("body");
        return PublicHolidayClient.parseItems(body);
    }

    @Test
    void 항목이_여러_개면_배열로_온다() {
        // 버그: 필드 이름 오타(locdate, dateName)는 에러 없이 날짜·이름만 틀리게 만들었음
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[
                {"dateKind":"01","dateName":"노동절","isHoliday":"Y","locdate":20260501,"seq":2},
                {"dateKind":"01","dateName":"어린이날","isHoliday":"Y","locdate":20260505,"seq":2},
                {"dateKind":"01","dateName":"부처님오신날","isHoliday":"Y","locdate":20260524,"seq":1},
                {"dateKind":"01","dateName":"대체공휴일(부처님오신날)","isHoliday":"Y","locdate":20260525,"seq":1}
                ]},"numOfRows":10,"pageNo":1,"totalCount":4}}}
                """;

        List<PublicHolidayItem> items = parse(json);

        assertEquals(4, items.size());
        assertEquals(LocalDate.of(2026, 5, 1), items.get(0).getDate());
        assertEquals("노동절", items.get(0).getName());
        assertEquals(LocalDate.of(2026, 5, 25), items.get(3).getDate());
        assertEquals("대체공휴일(부처님오신날)", items.get(3).getName());
    }

    @Test
    void 항목이_하나면_배열이_아니라_객체로_온다() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":
                {"dateKind":"01","dateName":"1월1일","isHoliday":"Y","locdate":20260101,"seq":1}
                },"numOfRows":10,"pageNo":1,"totalCount":1}}}
                """;

        List<PublicHolidayItem> items = parse(json);

        assertEquals(1, items.size());
        assertEquals(LocalDate.of(2026, 1, 1), items.get(0).getDate());
        assertEquals("1월1일", items.get(0).getName());
    }

    @Test
    void 항목이_없으면_items가_빈_문자열로_온다() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":"","numOfRows":10,"pageNo":1,"totalCount":0}}}
                """;

        assertTrue(parse(json).isEmpty());
    }

    @Test
    void 휴일이_아닌_항목은_빼고_이름은_앞뒤_공백을_지운다() {
        String json = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[
                {"dateKind":"02","dateName":"기념일","isHoliday":"N","locdate":20260502,"seq":1},
                {"dateKind":"01","dateName":"  어린이날  ","isHoliday":"Y","locdate":20260505,"seq":1}
                ]},"numOfRows":10,"pageNo":1,"totalCount":2}}}
                """;

        List<PublicHolidayItem> items = parse(json);

        assertEquals(1, items.size());
        assertEquals("어린이날", items.get(0).getName());
    }
}