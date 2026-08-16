package com.example.beyond_may_be.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginRequestDto;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpRequestDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @InjectMocks private UserService userService;

  @Mock private UserRepository userRepository;

  @Mock private AuthTokenService authTokenService;

  @DisplayName("회원가입에 성공한다.")
  @Test
  void signUp() {
    // given
    UserSignUpRequestDto requestDto = new UserSignUpRequestDto("testuser", null, null, null, null);
    User savedUser = User.builder().nickname("testuser").identificationCode(1).build();

    given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt()))
        .willReturn(false);
    given(userRepository.save(any(User.class))).willReturn(savedUser);
    given(authTokenService.issue(any())).willReturn("issued-token");

    // when
    UserSignUpResponseDto responseDto = userService.signUp(requestDto);

    // then
    assertThat(responseDto.getNickname()).isEqualTo("testuser");
    assertThat(responseDto.getIdentificationCode()).isNotNull();
    assertThat(responseDto.getToken()).isEqualTo("issued-token");
  }

  @DisplayName("성향 점수가 모두 비어있으면 preferenceType도 비어있다.")
  @Test
  void signUp_noScores_preferenceTypeIsNull() {
    // given
    UserSignUpRequestDto requestDto = new UserSignUpRequestDto("testuser", null, null, null, null);
    given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt()))
        .willReturn(false);
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(authTokenService.issue(any())).willReturn("issued-token");

    // when
    userService.signUp(requestDto);

    // then
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getPreferenceType()).isNull();
  }

  @DisplayName("가장 높은 점수의 유형으로 preferenceType을 계산한다.")
  @Test
  void signUp_calculatesPreferenceTypeFromHighestScore() {
    // given
    UserSignUpRequestDto requestDto = new UserSignUpRequestDto("testuser", 3, 5, 9, 2);
    given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt()))
        .willReturn(false);
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(authTokenService.issue(any())).willReturn("issued-token");

    // when
    userService.signUp(requestDto);

    // then
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getPreferenceType()).isEqualTo(TravelPreferenceType.ARTIST);
  }

  @DisplayName("점수가 동점이면 THINKER, FOODIE, ARTIST, REMEMBERER 순서로 결정한다.")
  @Test
  void signUp_tieBreaksByFixedPriority() {
    // given
    UserSignUpRequestDto requestDto = new UserSignUpRequestDto("testuser", 5, 5, 5, 5);
    given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt()))
        .willReturn(false);
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(authTokenService.issue(any())).willReturn("issued-token");

    // when
    userService.signUp(requestDto);

    // then
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    org.mockito.Mockito.verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getPreferenceType()).isEqualTo(TravelPreferenceType.THINKER);
  }

  @DisplayName("로그인에 성공한다.")
  @Test
  void login() {
    // given
    UserLoginRequestDto requestDto = new UserLoginRequestDto("testuser", 12);
    User user = User.builder().nickname("testuser").identificationCode(12).build();

    given(userRepository.findByNicknameAndIdentificationCode("testuser", 12))
        .willReturn(Optional.of(user));
    given(authTokenService.issue(any())).willReturn("issued-token");

    // when
    UserLoginResponseDto responseDto = userService.login(requestDto);

    // then
    assertThat(responseDto.getNickname()).isEqualTo("testuser");
    assertThat(responseDto.getToken()).isEqualTo("issued-token");
  }

  @DisplayName("잘못된 정보로 로그인 시 예외가 발생한다.")
  @Test
  void login_fail() {
    // given
    UserLoginRequestDto requestDto = new UserLoginRequestDto("testuser", 12);

    given(userRepository.findByNicknameAndIdentificationCode("testuser", 12))
        .willReturn(Optional.empty());

    // when & then
    assertThrows(UserHandler.class, () -> userService.login(requestDto));
  }

  @DisplayName("나의 성향을 조회하면 유형과 유형별 점수를 함께 반환한다.")
  @Test
  void getMyPreference_returnsTypeAndScores() {
    // given
    User user =
        User.builder()
            .nickname("testuser")
            .identificationCode(12)
            .preferenceType(TravelPreferenceType.ARTIST)
            .thinkerScore(1)
            .foodieScore(2)
            .artistScore(4)
            .remembererScore(0)
            .build();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    // when
    var responseDto = userService.getMyPreference(1L);

    // then
    assertThat(responseDto.getPreferenceType()).isEqualTo(TravelPreferenceType.ARTIST);
    assertThat(responseDto.getThinkerScore()).isEqualTo(1);
    assertThat(responseDto.getFoodieScore()).isEqualTo(2);
    assertThat(responseDto.getArtistScore()).isEqualTo(4);
    assertThat(responseDto.getRemembererScore()).isEqualTo(0);
  }

  @DisplayName("존재하지 않는 사용자의 성향을 조회하면 예외가 발생한다.")
  @Test
  void getMyPreference_userNotFound_throws() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThrows(UserHandler.class, () -> userService.getMyPreference(1L));
  }
}
