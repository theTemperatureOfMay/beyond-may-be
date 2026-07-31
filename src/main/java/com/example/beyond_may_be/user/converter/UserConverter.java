package com.example.beyond_may_be.user.converter;

import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;

public final class UserConverter {
    private UserConverter() {}

    public static UserSignUpResponseDto toSignUpResponse(User user) {
        return new UserSignUpResponseDto(user.getId(), user.getNickname(), user.getIdentificationCode());
    }

    public static UserLoginResponseDto toLoginResponse(User user) {
        return new UserLoginResponseDto(user.getId(), user.getNickname());
    }
}
