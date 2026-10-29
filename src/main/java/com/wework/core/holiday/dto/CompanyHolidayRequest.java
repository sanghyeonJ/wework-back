package com.wework.core.holiday.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class CompanyHolidayRequest {

    @NotNull
    private LocalDate holidayDate;

    @NotBlank
    @Size(max = 100)
    private String name;

    // boolean이 아니라 Boolean: 안 보내면 null → @NotNull이 400으로 잡음
    @NotNull
    private Boolean recurring;

}
