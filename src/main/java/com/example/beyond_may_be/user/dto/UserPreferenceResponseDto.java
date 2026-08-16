package com.example.beyond_may_be.user.dto;

import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPreferenceResponseDto {
  private Long userId;
  private String nickname;
  private TravelPreferenceType preferenceType;
  private Integer thinkerScore;
  private Integer foodieScore;
  private Integer artistScore;
  private Integer remembererScore;
}
