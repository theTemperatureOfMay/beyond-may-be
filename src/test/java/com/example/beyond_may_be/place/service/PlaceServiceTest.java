package com.example.beyond_may_be.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

  @InjectMocks private PlaceService placeService;

  @Mock private PlaceRepository placeRepository;

  @DisplayName("해당 유형의 활성 장소가 6곳을 초과하면 6곳만 랜덤으로 반환한다.")
  @Test
  void getRecommendations_moreThanSix_returnsSix() {
    // given
    List<Place> places =
        java.util.stream.IntStream.rangeClosed(1, 10)
            .mapToObj(i -> place("장소" + i, TravelPreferenceType.THINKER))
            .toList();
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.THINKER))
        .willReturn(places);

    // when
    PlaceRecommendationResponse response =
        placeService.getRecommendations(TravelPreferenceType.THINKER);

    // then
    assertThat(response.places()).hasSize(6);
    List<String> allNames = places.stream().map(Place::getName).toList();
    assertThat(response.places()).allMatch(p -> allNames.contains(p.name()));
    assertThat(response.places().stream().map(p -> p.name()).distinct()).hasSize(6);
  }

  @DisplayName("해당 유형의 활성 장소가 6곳 미만이면 있는 만큼만 반환한다.")
  @Test
  void getRecommendations_fewerThanSix_returnsAll() {
    // given
    List<Place> places =
        List.of(
            place("장소1", TravelPreferenceType.FOODIE), place("장소2", TravelPreferenceType.FOODIE));
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.FOODIE))
        .willReturn(places);

    // when
    PlaceRecommendationResponse response =
        placeService.getRecommendations(TravelPreferenceType.FOODIE);

    // then
    assertThat(response.places()).hasSize(2);
  }

  @DisplayName("해당 유형의 활성 장소가 없으면 빈 목록을 반환한다.")
  @Test
  void getRecommendations_none_returnsEmpty() {
    // given
    given(placeRepository.findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType.ARTIST))
        .willReturn(List.of());

    // when
    PlaceRecommendationResponse response =
        placeService.getRecommendations(TravelPreferenceType.ARTIST);

    // then
    assertThat(response.places()).isEmpty();
  }

  private Place place(String name, TravelPreferenceType type) {
    return Place.builder()
        .name(name)
        .category("카테고리")
        .travelMbtiType(type)
        .tags(List.of("태그"))
        .address("주소")
        .latitude(BigDecimal.valueOf(35.1595))
        .longitude(BigDecimal.valueOf(126.8526))
        .businessHours("09:00-18:00")
        .description("설명")
        .thumbnailUrl(null)
        .active(true)
        .build();
  }
}
