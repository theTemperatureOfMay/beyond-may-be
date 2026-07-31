package com.example.beyond_may_be.user.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.user.converter.UserConverter;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginRequestDto;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpRequestDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserSignUpResponseDto signUp(UserSignUpRequestDto requestDto) {
        String nickname = requestDto.getNickname();
        Integer identificationCode = generateUniqueIdentificationCode(nickname);

        User user = User.builder()
            .nickname(nickname)
            .identificationCode(identificationCode)
            .build();

        User savedUser = userRepository.save(user);

        return UserConverter.toSignUpResponse(savedUser);
    }

    private Integer generateUniqueIdentificationCode(String nickname) {
        Random random = new Random();
        Integer code;
        do {
            code = random.nextInt(99) + 1;
        } while (userRepository.existsByNicknameAndIdentificationCode(nickname, code));
        return code;
    }

    @Transactional(readOnly = true)
    public UserLoginResponseDto login(UserLoginRequestDto requestDto) {
        User user = userRepository.findByNicknameAndIdentificationCode(requestDto.getNickname(), requestDto.getIdentificationCode())
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_LOGIN_FAILED));
        return UserConverter.toLoginResponse(user);
    }
}