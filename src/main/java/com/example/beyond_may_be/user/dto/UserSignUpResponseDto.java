package com.example.beyond_may_be.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSignUpResponseDto {
    private Long userId;
    private String nickname;
    private Integer identificationCode;
}
