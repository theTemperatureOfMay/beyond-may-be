package com.example.beyond_may_be;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceDetailIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private PlaceRepository placeRepository;

  @Test
  void firstDetailResponseIncludesFallbackForMissingDescription() throws Exception {
    Place place =
        placeRepository.save(
            Place.builder()
                .name("설명 없는 장소")
                .category("전시")
                .travelMbtiType(TravelPreferenceType.ARTIST)
                .tags(List.of())
                .address("광주광역시")
                .latitude(new BigDecimal("35.1"))
                .longitude(new BigDecimal("126.8"))
                .active(true)
                .build());

    mockMvc
        .perform(get("/api/v1/places/{placeId}", place.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("상세 설명 정보 없음"));
  }
}
