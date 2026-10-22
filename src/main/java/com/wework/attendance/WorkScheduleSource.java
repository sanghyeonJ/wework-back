package com.wework.attendance;

// 적용되는 근무시간이 어디서 왔는지
public enum WorkScheduleSource {

    MEMBER,  // 개인 설정
    COMPANY, // 회사 일괄 설정
    DEFAULT  // 설정 없음 → 코드 기본값

}
