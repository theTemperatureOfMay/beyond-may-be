package com.example.beyond_may_be.user.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.user.converter.UserConverter;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginRequestDto;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserPreferenceResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpRequestDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

  private static final List<TravelPreferenceType> PREFERENCE_TYPE_PRIORITY =
      List.of(
          TravelPreferenceType.THINKER,
          TravelPreferenceType.FOODIE,
          TravelPreferenceType.ARTIST,
          TravelPreferenceType.REMEMBERER);

  private final UserRepository userRepository;
  private final AuthTokenService authTokenService;

  public UserSignUpResponseDto signUp(UserSignUpRequestDto requestDto) {
    String nickname = requestDto.getNickname();
    Integer identificationCode = generateUniqueIdentificationCode(nickname);
    TravelPreferenceType preferenceType =
        resolvePreferenceType(
            requestDto.getThinkerScore(),
            requestDto.getFoodieScore(),
            requestDto.getArtistScore(),
            requestDto.getRemembererScore());

    User user =
        User.builder()
            .nickname(nickname)
            .identificationCode(identificationCode)
            .preferenceType(preferenceType)
            .thinkerScore(requestDto.getThinkerScore())
            .foodieScore(requestDto.getFoodieScore())
            .artistScore(requestDto.getArtistScore())
            .remembererScore(requestDto.getRemembererScore())
            .build();

    User savedUser = userRepository.save(user);
    String token = authTokenService.issue(savedUser.getId());

    return UserConverter.toSignUpResponse(savedUser, token);
  }

  private Integer generateUniqueIdentificationCode(String nickname) {
    Random random = new Random();
    Integer code;
    do {
      code = random.nextInt(99) + 1;
    } while (userRepository.existsByNicknameAndIdentificationCode(nickname, code));
    return code;
  }

  private TravelPreferenceType resolvePreferenceType(
      Integer thinkerScore, Integer foodieScore, Integer artistScore, Integer remembererScore) {
    if (thinkerScore == null
        && foodieScore == null
        && artistScore == null
        && remembererScore == null) {
      return null;
    }

    List<Integer> scoresByPriority =
        List.of(
            orZero(thinkerScore),
            orZero(foodieScore),
            orZero(artistScore),
            orZero(remembererScore));

    TravelPreferenceType best = null;
    int bestScore = Integer.MIN_VALUE;
    for (int i = 0; i < PREFERENCE_TYPE_PRIORITY.size(); i++) {
      int score = scoresByPriority.get(i);
      if (score > bestScore) {
        bestScore = score;
        best = PREFERENCE_TYPE_PRIORITY.get(i);
      }
    }
    return best;
  }

  private int orZero(Integer value) {
    return value == null ? 0 : value;
  }

  public UserLoginResponseDto login(UserLoginRequestDto requestDto) {
    User user =
        userRepository
            .findByNicknameAndIdentificationCode(
                requestDto.getNickname(), requestDto.getIdentificationCode())
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_LOGIN_FAILED));
    String token = authTokenService.issue(user.getId());
    return UserConverter.toLoginResponse(user, token);
  }

  @Transactional(readOnly = true)
  public UserPreferenceResponseDto getMyPreference(Long userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    return UserConverter.toPreferenceResponse(user);
  }
}
