package com.wework.attendance;

import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.repository.LeaveRequestRepository;
import com.wework.core.leave.service.LeaveRequestService;
import com.wework.core.member.entity.Member;
import com.wework.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 반차인 날 지각·조퇴 건너뛰기(출근·퇴근) + 관리자 근태 조회의 휴가 종류 + 승인된 휴가 조회(서비스)
class AttendanceLeaveTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;
    @Autowired
    private LeaveRequestService leaveRequestService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private Company company;
    private Member admin;
    private Member hong;
    private Member kim;
    private LocalDate today;

    @BeforeEach
    void initLeaveTest() throws Exception {
        company = createCompany("위워크 서울", "100-00-00001");
        var department = createDepartment(company, null, "개발팀");
        admin = createAdmin(company, "admin@leave-test.com");
        hong = createEmployee(company, department, "hong@leave-test.com");
        kim = createEmployee(company, department, "kim@leave-test.com");
        today = LocalDate.now(SEOUL);

        // 00:00~23:59, 유예 0분 → 휴가가 없으면 언제 출근·퇴근해도 항상 지각·조퇴
        mockMvc.perform(put("/api/attendance/manage/schedules/company")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"00:00\",\"endTime\":\"23:59\",\"graceMinutes\":0}"))
                .andExpect(status().is2xxSuccessful());
    }

    // ===== 출근: 지각 판단 =====

    @Test
    void 휴가가_없으면_지각이다() throws Exception {
        assertCheckInLate(hong, true);
    }

    @Test
    void 오전_반차가_승인된_날은_지각이_아니다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, false);
    }

    @Test
    void 오후_반차가_승인된_날은_지각_판단이_그대로다() throws Exception {
        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 연차가_승인된_날도_지각_판단이_그대로다() throws Exception {
        saveLeave(hong, LeaveType.ANNUAL, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 병가가_승인된_날도_지각_판단이_그대로다() throws Exception {
        saveLeave(hong, LeaveType.SICK, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 승인_대기_중인_오전_반차는_지각을_건너뛰지_않는다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.PENDING);
        assertCheckInLate(hong, true);
    }

    @Test
    void 반려된_오전_반차는_지각을_건너뛰지_않는다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.REJECTED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 취소된_오전_반차는_지각을_건너뛰지_않는다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.CANCELED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 어제의_오전_반차는_오늘_지각에_영향이_없다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today.minusDays(1), today.minusDays(1), LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, true);
    }

    @Test
    void 오전_오후_반차가_둘_다_있으면_지각이_아니다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);
        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, false);
    }

    @Test
    void 출근한_뒤에_반차가_승인되어도_저장된_지각은_그대로다() throws Exception {
        assertCheckInLate(hong, true);
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckInLate(hong, true); // 이미 출근한 날은 기존 기록을 그대로 돌려줌
    }

    // ===== 퇴근: 조퇴 판단 =====

    @Test
    void 휴가가_없으면_조퇴다() throws Exception {
        assertCheckOutEarlyLeave(hong, true);
    }

    @Test
    void 오후_반차가_승인된_날은_조퇴가_아니다() throws Exception {
        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckOutEarlyLeave(hong, false);
    }

    @Test
    void 오전_반차가_승인된_날은_조퇴_판단이_그대로다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckOutEarlyLeave(hong, true);
    }

    @Test
    void 연차가_승인된_날도_조퇴_판단이_그대로다() throws Exception {
        saveLeave(hong, LeaveType.ANNUAL, today, today, LeaveRequestStatus.APPROVED);
        assertCheckOutEarlyLeave(hong, true);
    }

    @Test
    void 승인_대기_중인_오후_반차는_조퇴를_건너뛰지_않는다() throws Exception {
        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.PENDING);
        assertCheckOutEarlyLeave(hong, true);
    }

    @Test
    void 오전_오후_반차가_둘_다_있으면_조퇴가_아니다() throws Exception {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);
        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.APPROVED);
        assertCheckOutEarlyLeave(hong, false);
    }

    @Test
    void 퇴근을_다시_누르면_그때의_휴가로_조퇴를_다시_판단한다() throws Exception {
        checkIn(hong).andExpect(status().is2xxSuccessful());
        checkOut(hong).andExpect(jsonPath("$.earlyLeave").value(true));

        saveLeave(hong, LeaveType.HALF_PM, today, today, LeaveRequestStatus.APPROVED);
        checkOut(hong).andExpect(jsonPath("$.earlyLeave").value(false));
    }

    // ===== 관리자 근태 조회: leaveTypes =====

    @Test
    void 휴가_중인_사원은_종류가_담기고_휴가가_없는_사원은_빈_배열이다() throws Exception {
        LocalDate date = today.plusDays(7);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.APPROVED);

        assertThat(leaveTypesOf(findRow(date, hong))).containsExactly("HALF_AM");
        assertThat(leaveTypesOf(findRow(date, kim))).isEmpty(); // null이 아니라 빈 배열
    }

    @Test
    void 조회한_날짜의_휴가가_나온다() throws Exception {
        LocalDate date = today.plusDays(7);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.APPROVED);

        assertThat(leaveTypesOf(findRow(date, hong))).containsExactly("HALF_AM");
        assertThat(leaveTypesOf(findRow(today, hong))).isEmpty(); // 오늘 행에는 안 나옴
    }

    @Test
    void 같은_날_오전_오후_반차는_둘_다_나온다() throws Exception {
        LocalDate date = today.plusDays(7);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.APPROVED);
        saveLeave(hong, LeaveType.HALF_PM, date, date, LeaveRequestStatus.APPROVED);

        assertThat(leaveTypesOf(findRow(date, hong))).containsExactlyInAnyOrder("HALF_AM", "HALF_PM");
    }

    @Test
    void 승인_대기_반려_취소된_휴가는_나오지_않는다() throws Exception {
        LocalDate date = today.plusDays(7);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.PENDING);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.REJECTED);
        saveLeave(hong, LeaveType.HALF_AM, date, date, LeaveRequestStatus.CANCELED);

        assertThat(leaveTypesOf(findRow(date, hong))).isEmpty();
    }

    @Test
    void 병가도_종류에_담긴다() throws Exception {
        LocalDate date = today.plusDays(7);
        saveLeave(hong, LeaveType.SICK, date, date, LeaveRequestStatus.APPROVED);

        assertThat(leaveTypesOf(findRow(date, hong))).containsExactly("SICK");
    }

    @Test
    void 범위를_넘긴_페이지는_에러_없이_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/attendance/manage/records")
                        .header("Authorization", bearer(admin))
                        .param("date", today.toString())
                        .param("page", "50")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ===== 승인된 휴가 조회 (LeaveRequestService.getApprovedLeaveTypes) =====

    @Test
    void 사원_목록이_비어_있으면_빈_Map이다() {
        Map<Long, List<LeaveType>> result = leaveRequestService.getApprovedLeaveTypes(company.getId(), List.of(), today);

        assertThat(result).isEmpty();
    }

    @Test
    void 기간_시작일_앞날은_안_나오고_시작일은_나온다() {
        LocalDate start = today.plusDays(10);
        saveLeave(hong, LeaveType.ANNUAL, start, start.plusDays(2), LeaveRequestStatus.APPROVED);

        assertThat(leaveRequestService.getApprovedLeaveTypes(company.getId(), List.of(hong.getId()), start.minusDays(1))).isEmpty();
        assertThat(leaveRequestService.getApprovedLeaveTypes(company.getId(), List.of(hong.getId()), start).get(hong.getId()))
                .containsExactly(LeaveType.ANNUAL);
    }

    @Test
    void 기간_종료일은_나오고_종료일_다음날은_안_나온다() {
        LocalDate start = today.plusDays(10);
        LocalDate end = start.plusDays(2);
        saveLeave(hong, LeaveType.ANNUAL, start, end, LeaveRequestStatus.APPROVED);

        assertThat(leaveRequestService.getApprovedLeaveTypes(company.getId(), List.of(hong.getId()), end).get(hong.getId()))
                .containsExactly(LeaveType.ANNUAL);
        assertThat(leaveRequestService.getApprovedLeaveTypes(company.getId(), List.of(hong.getId()), end.plusDays(1))).isEmpty();
    }

    @Test
    void 다른_회사_번호로_조회하면_안_나온다() {
        Company other = createCompany("다른 회사", "100-00-00002");
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);

        Map<Long, List<LeaveType>> result = leaveRequestService.getApprovedLeaveTypes(other.getId(), List.of(hong.getId()), today);

        assertThat(result).isEmpty();
    }

    @Test
    void 여러_사원을_한_번에_조회하면_휴가가_있는_사원만_키로_담긴다() {
        saveLeave(hong, LeaveType.HALF_AM, today, today, LeaveRequestStatus.APPROVED);

        Map<Long, List<LeaveType>> result = leaveRequestService.getApprovedLeaveTypes(
                company.getId(), List.of(hong.getId(), kim.getId()), today);

        assertThat(result).containsOnlyKeys(hong.getId());
        assertThat(result.get(hong.getId())).containsExactly(LeaveType.HALF_AM);
    }

    // ===== 도우미 =====

    // 휴가 신청 API를 거치지 않고 원하는 상태로 바로 저장
    private LeaveRequest saveLeave(Member member, LeaveType type, LocalDate start, LocalDate end, LeaveRequestStatus status) {
        BigDecimal days = type.isHalfDay() ? new BigDecimal("0.5") : BigDecimal.ONE;
        LeaveRequest leave = LeaveRequest.create(member.getCompany(), member, type, start, end, days, null);
        switch (status) {
            case APPROVED -> leave.approve();
            case REJECTED -> leave.reject();
            case CANCELED -> leave.cancel();
            case PENDING -> { }
        }
        return leaveRequestRepository.save(leave);
    }

    private ResultActions checkIn(Member member) throws Exception {
        return mockMvc.perform(post("/api/attendance/check-in").header("Authorization", bearer(member)));
    }

    private ResultActions checkOut(Member member) throws Exception {
        return mockMvc.perform(post("/api/attendance/check-out").header("Authorization", bearer(member)));
    }

    private void assertCheckInLate(Member member, boolean expectedLate) throws Exception {
        checkIn(member)
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.late").value(expectedLate));
    }

    // 퇴근은 출근 기록이 있어야 하므로 먼저 출근
    private void assertCheckOutEarlyLeave(Member member, boolean expectedEarlyLeave) throws Exception {
        checkIn(member).andExpect(status().is2xxSuccessful());
        checkOut(member)
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.earlyLeave").value(expectedEarlyLeave));
    }

    // 관리자 근태 조회 응답에서 한 사원의 줄을 찾음
    private JsonNode findRow(LocalDate date, Member target) throws Exception {
        String json = mockMvc.perform(get("/api/attendance/manage/records")
                        .header("Authorization", bearer(admin))
                        .param("date", date.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(json).path("content");
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).path("memberId").asLong() == target.getId()) {
                return content.get(i);
            }
        }
        throw new AssertionError("근태 조회 목록에 사원이 없음: " + target.getId());
    }

    private List<String> leaveTypesOf(JsonNode row) {
        JsonNode node = row.path("leaveTypes");
        assertThat(node.isArray()).as("leaveTypes는 null이 아니라 배열이어야 함").isTrue();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            result.add(node.get(i).asString());
        }
        return result;
    }
}