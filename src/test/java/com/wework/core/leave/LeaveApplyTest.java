package com.wework.core.leave;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 휴가 신청 API 테스트: 성공(저장·결재 등록·연결), 종류·기간·일수, 겹침, 잔여, 결재자, 입력 형식, 인증.
 * 실패 케이스는 이 테스트가 한 트랜잭션이라 도중 실패 시 먼저 저장한 행이 남으므로,
 * 저장 뒤에 실패하는 결재자 오류는 메서드를 나눠 한 번에 하나만 확인한다.
 */
class LeaveApplyTest extends LeaveTestSupport {

    // ===== 성공 =====

    @Test
    void 정상_신청하면_201이고_대기로_저장되며_결재_요청과_단계가_만들어지고_연결된다() throws Exception {
        grant(hong, "15");

        Long id = applyOk(hong, "ANNUAL", MONDAY, MONDAY, kim, park);

        LeaveRequest leave = leaveOf(id);
        assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(leave.getDays()).isEqualByComparingTo("1");
        assertThat(leave.getMember().getId()).isEqualTo(hong.getId());
        assertThat(leave.getCompany().getId()).isEqualTo(company.getId());
        assertThat(leave.getReason()).isEqualTo("가족 행사");
        assertThat(leave.getApprovalRequestId()).isNotNull();

        ApprovalRequest approval = approvalRequestRepository.findById(leave.getApprovalRequestId()).orElseThrow();
        assertThat(approval.getTargetType()).isEqualTo("LEAVE");
        assertThat(approval.getTargetId()).isEqualTo(id);
        assertThat(approval.getTitle()).isEqualTo("휴가 신청 " + MONDAY + " ~ " + MONDAY + " (1일)");
        assertThat(approval.getRequester().getId()).isEqualTo(hong.getId());

        List<ApprovalStep> steps = approvalStepRepository
                .findAllByRequestIdAndCompanyIdOrderByStepAsc(approval.getId(), company.getId());
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getApprover().getId()).isEqualTo(kim.getId());
        assertThat(steps.get(1).getApprover().getId()).isEqualTo(park.getId());
    }

    @Test
    void 신청_직후_잔액_응답에는_승인_대기_일수가_나오고_사용_일수는_그대로다() throws Exception {
        grant(hong, "15");
        applyOk(hong, "ANNUAL", MONDAY, TUESDAY, kim);

        getBalance(hong)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedDays").value(15.0))
                .andExpect(jsonPath("$.usedDays").value(0.0))
                .andExpect(jsonPath("$.remainingDays").value(15.0))
                .andExpect(jsonPath("$.pendingDays").value(2.0));
    }

    @Test
    void 사유_없이도_신청할_수_있다() throws Exception {
        grant(hong, "15");

        String json = body("ANNUAL", MONDAY, MONDAY, null, kim);
        applyRaw(hong, json).andExpect(status().isCreated());
    }

    // ===== 종류·기간·일수 =====

    @Test
    void 병가는_아직_지원하지_않아_거절된다() throws Exception {
        grant(hong, "15");
        long before = leaveRequestRepository.count();

        expectError(apply(hong, "SICK", MONDAY, MONDAY, kim), status().isBadRequest(), "LEAVE_TYPE_NOT_SUPPORTED");

        assertThat(leaveRequestRepository.count()).isEqualTo(before);
    }

    @Test
    void 근무일이_없으면_거절되고_쉬는_날_반차도_거절된다_평일_반차는_성공한다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", SATURDAY, SUNDAY, kim), status().isBadRequest(), "NO_WORKING_DAY");
        expectError(apply(hong, "HALF_AM", SATURDAY, SATURDAY, kim), status().isBadRequest(), "NO_WORKING_DAY");

        apply(hong, "HALF_AM", MONDAY, MONDAY, kim).andExpect(status().isCreated());
    }

    @Test
    void 기간이_올바르지_않으면_거절된다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", TUESDAY, MONDAY, kim), status().isBadRequest(), "INVALID_LEAVE_PERIOD");
        expectError(apply(hong, "ANNUAL", LocalDate.of(YEAR, 12, 31), LocalDate.of(YEAR + 1, 1, 1), kim),
                status().isBadRequest(), "INVALID_LEAVE_PERIOD");
        expectError(apply(hong, "HALF_AM", MONDAY, TUESDAY, kim), status().isBadRequest(), "INVALID_LEAVE_PERIOD");
    }

    // ===== 겹침 =====

    @Test
    void 기간이_겹치면_거절된다_하루_맞닿음과_감쌈과_안쪽도_거절_앞뒤는_성공한다() throws Exception {
        grant(hong, "15");
        applyOk(hong, "ANNUAL", TUESDAY, WEDNESDAY, kim);

        expectError(apply(hong, "ANNUAL", MONDAY, TUESDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");     // 끝이 기존 시작일에 맞닿음
        expectError(apply(hong, "ANNUAL", WEDNESDAY, THURSDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED"); // 시작이 기존 종료일에 맞닿음
        expectError(apply(hong, "ANNUAL", MONDAY, FRIDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");      // 기존을 통째로 감쌈
        expectError(apply(hong, "ANNUAL", TUESDAY, TUESDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");    // 안쪽

        apply(hong, "ANNUAL", MONDAY, MONDAY, kim).andExpect(status().isCreated());     // 앞
        apply(hong, "ANNUAL", THURSDAY, THURSDAY, kim).andExpect(status().isCreated()); // 뒤
    }

    @Test
    void 다른_사원의_같은_날_신청과는_겹치지_않는다() throws Exception {
        grant(hong, "15");
        grant(kim, "15");
        applyOk(kim, "ANNUAL", MONDAY, MONDAY, park);

        apply(hong, "ANNUAL", MONDAY, MONDAY, kim).andExpect(status().isCreated());
    }

    @Test
    void 반차는_반대_반차와만_같은_날_신청할_수_있다() throws Exception {
        grant(hong, "15");
        applyOk(hong, "HALF_AM", MONDAY, MONDAY, kim);

        expectError(apply(hong, "HALF_AM", MONDAY, MONDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED"); // 같은 종류
        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");  // 연차는 반차와도 불가
        apply(hong, "HALF_PM", MONDAY, MONDAY, kim).andExpect(status().isCreated());                         // 반대 반차는 성공

        expectError(apply(hong, "HALF_AM", MONDAY, MONDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED"); // 이미 하루가 참
        expectError(apply(hong, "HALF_PM", MONDAY, MONDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");
    }

    @Test
    void 연차가_있는_날에는_반차를_신청할_수_없다() throws Exception {
        grant(hong, "15");
        applyOk(hong, "ANNUAL", THURSDAY, THURSDAY, kim);

        expectError(apply(hong, "HALF_PM", THURSDAY, THURSDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED");
    }

    // ===== 잔여 일수 =====

    @Test
    void 잔여_경계_정확히_다_쓰는_신청은_성공하고_하루_넘으면_거절된다() throws Exception {
        grant(hong, "3");

        expectError(apply(hong, "ANNUAL", MONDAY, THURSDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE"); // 4일
        apply(hong, "ANNUAL", MONDAY, WEDNESDAY, kim).andExpect(status().isCreated());                                   // 3일
    }

    @Test
    void 승인_대기_중인_일수도_잔여에서_빠진다() throws Exception {
        grant(hong, "3");
        applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);   // 3일 대기 (아직 장부의 사용 일수는 0)

        expectError(apply(hong, "ANNUAL", THURSDAY, THURSDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE");
        expectError(apply(hong, "HALF_AM", FRIDAY, FRIDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE");
    }

    @Test
    void 반일_경계_0_5일이_남았으면_반차_한_번만_성공한다() throws Exception {
        grant(hong, "0.5");

        apply(hong, "HALF_AM", MONDAY, MONDAY, kim).andExpect(status().isCreated());

        expectError(apply(hong, "HALF_PM", MONDAY, MONDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE");
        expectError(apply(hong, "HALF_PM", TUESDAY, TUESDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE");
    }

    @Test
    void 반려된_신청은_겹침과_대기_일수에서_빠져_같은_기간으로_다시_신청할_수_있다() throws Exception {
        grant(hong, "3");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);

        expectError(apply(hong, "ANNUAL", MONDAY, WEDNESDAY, kim), status().isConflict(), "LEAVE_OVERLAPPED"); // 대기 중이면 겹침

        approvalService.reject(company.getId(), kim.getId(), approvalIdOf(id), null);

        apply(hong, "ANNUAL", MONDAY, WEDNESDAY, kim).andExpect(status().isCreated());
    }

    @Test
    void 장부가_없으면_잔여가_0이라_거절된다() throws Exception {
        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, kim), status().isConflict(), "INSUFFICIENT_LEAVE_BALANCE");
    }

    // ===== 결재자 (저장 뒤에 엔진이 거절하므로 메서드를 나눔) =====

    @Test
    void 결재자가_본인이면_거절된다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, hong), status().isBadRequest(), "INVALID_APPROVER");
    }

    @Test
    void 결재자가_중복이면_거절된다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, kim, kim), status().isBadRequest(), "INVALID_APPROVER");
    }

    @Test
    void 다른_회사_결재자가_섞이면_404이다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, kim, otherApprover), status().isNotFound(), "MEMBER_NOT_FOUND");
    }

    @Test
    void 퇴사한_결재자는_거절된다() throws Exception {
        grant(hong, "15");
        Member resigned = createEmployee(company, department, "resigned@leave.test");
        resigned.resign(LocalDate.now(ZoneId.of("Asia/Seoul")));

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, resigned), status().isBadRequest(), "INVALID_APPROVER");
    }

    @Test
    void 결재자가_없으면_400이다() throws Exception {
        grant(hong, "15");

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY), status().isBadRequest(), "INVALID_INPUT");
    }

    @Test
    void 결재자가_6명이면_400이다() throws Exception {
        grant(hong, "15");
        Member[] six = new Member[6];
        for (int i = 0; i < six.length; i++) {
            six[i] = createEmployee(company, department, "approver" + i + "@leave.test");
        }

        expectError(apply(hong, "ANNUAL", MONDAY, MONDAY, six), status().isBadRequest(), "INVALID_INPUT");
    }

    // ===== 입력 형식 =====

    @Test
    void 필수값이_빠지거나_형식이_틀리면_400이다() throws Exception {
        grant(hong, "15");
        String ids = "\"approverIds\":[" + kim.getId() + "]";

        String noType = "{\"startDate\":\"" + MONDAY + "\",\"endDate\":\"" + MONDAY + "\"," + ids + "}";
        String noStart = "{\"leaveType\":\"ANNUAL\",\"endDate\":\"" + MONDAY + "\"," + ids + "}";
        String noEnd = "{\"leaveType\":\"ANNUAL\",\"startDate\":\"" + MONDAY + "\"," + ids + "}";
        for (String invalid : new String[]{noType, noStart, noEnd}) {
            expectError(applyRaw(hong, invalid), status().isBadRequest(), "INVALID_INPUT");
        }

        applyRaw(hong, body("ABC", MONDAY, MONDAY, "가족 행사", kim)).andExpect(status().isBadRequest());
        applyRaw(hong, "{\"leaveType\":\"ANNUAL\",\"startDate\":\"abc\",\"endDate\":\"abc\"," + ids + "}")
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/leave/requests")
                        .header("Authorization", bearer(hong))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 사유는_500자까지_허용하고_501자부터_400이다() throws Exception {
        grant(hong, "15");

        expectError(applyRaw(hong, body("ANNUAL", MONDAY, MONDAY, "a".repeat(501), kim)),
                status().isBadRequest(), "INVALID_INPUT");
        applyRaw(hong, body("ANNUAL", MONDAY, MONDAY, "a".repeat(500), kim)).andExpect(status().isCreated());
    }

    // ===== 인증 =====

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/leave/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ANNUAL", MONDAY, MONDAY, "x", kim)))
                .andExpect(status().isUnauthorized());
    }
}