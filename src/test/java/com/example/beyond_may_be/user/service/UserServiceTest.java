package com.example.beyond_may_be.user.service;

import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginRequestDto;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpRequestDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;
import com.example.beyond_may_be.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @DisplayName("회원가입에 성공한다.")
    @Test
    void signUp() {
        // given
        UserSignUpRequestDto requestDto = new UserSignUpRequestDto("testuser");
        User savedUser = User.builder().nickname("testuser").identificationCode(1).build();

        given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt())).willReturn(false);
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        UserSignUpResponseDto responseDto = userService.signUp(requestDto);

        // then
        assertThat(responseDto.getNickname()).isEqualTo("testuser");
        assertThat(responseDto.getIdentificationCode()).isNotNull();
    }

    @DisplayName("로그인에 성공한다.")
    @Test
    void login() {
        // given
        UserLoginRequestDto requestDto = new UserLoginRequestDto("testuser", 12);
        User user = User.builder().nickname("testuser").identificationCode(12).build();

        given(userRepository.findByNicknameAndIdentificationCode("testuser", 12)).willReturn(Optional.of(user));

        // when
        UserLoginResponseDto responseDto = userService.login(requestDto);

        // then
        assertThat(responseDto.getNickname()).isEqualTo("testuser");
    }

    @DisplayName("잘못된 정보로 로그인 시 예외가 발생한다.")
    @Test
    void login_fail() {
        // given
        UserLoginRequestDto requestDto = new UserLoginRequestDto("testuser", 12);

        given(userRepository.findByNicknameAndIdentificationCode("testuser", 12)).willReturn(Optional.empty());

        // when & then
        assertThrows(UserHandler.class, () -> userService.login(requestDto));
    }
}