package com.wework.core.holiday.dto;

import lombok.Getter;

@Getter
public class PublicHolidaySyncResponse {

    private final String countryCode;
    private final int year;
    private final int fetchedCount;  // API에서 받은 휴일 수
    private final int addedCount;  // 이번에 새로 저장한 수

    private PublicHolidaySyncResponse(String countryCode, int year, int fetchedCount, int addedCount) {
        this.countryCode = countryCode;
        this.year = year;
        this.fetchedCount = fetchedCount;
        this.addedCount = addedCount;
    }

    public static PublicHolidaySyncResponse of(String countryCode, int year, int fetchedCount, int addedCount) {
        return new PublicHolidaySyncResponse(countryCode, year, fetchedCount, addedCount);
    }

}
