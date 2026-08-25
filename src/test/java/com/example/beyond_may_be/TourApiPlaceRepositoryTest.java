package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class TourApiPlaceRepositoryTest {

  @Autowired private PlaceRepository placeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void insertsOnceAndKeepsNullableDetailsNullOnConflict() {
    int inserted = insert("최초 이름");
    int duplicate = insert("수정하면 안 되는 이름");

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            """
            SELECT name, business_hours, description, thumbnail_url
            FROM places
            WHERE tour_content_id = 999999001
            """);
    assertThat(inserted).isEqualTo(1);
    assertThat(duplicate).isZero();
    assertThat(stored)
        .containsEntry("name", "최초 이름")
        .containsEntry("business_hours", null)
        .containsEntry("description", null)
        .containsEntry("thumbnail_url", null);
  }

  @Test
  void enrichesOnlyMissingDetailsWithoutOverwritingThem() {
    insert("보강 대상");
    Long placeId =
        jdbcTemplate.queryForObject(
            "SELECT place_id FROM places WHERE tour_content_id = 999999001", Long.class);

    int descriptionUpdated = placeRepository.updateDescriptionIfMissing(placeId, "최초 설명");
    int hoursUpdated = placeRepository.updateBusinessHoursIfMissing(placeId, "09:00~18:00");
    int descriptionOverwrite = placeRepository.updateDescriptionIfMissing(placeId, "덮어쓸 설명");
    int hoursOverwrite = placeRepository.updateBusinessHoursIfMissing(placeId, "00:00~24:00");

    Map<String, Object> stored =
        jdbcTemplate.queryForMap(
            "SELECT description, business_hours FROM places WHERE place_id = ?", placeId);
    assertThat(descriptionUpdated).isEqualTo(1);
    assertThat(hoursUpdated).isEqualTo(1);
    assertThat(descriptionOverwrite).isZero();
    assertThat(hoursOverwrite).isZero();
    assertThat(stored)
        .containsEntry("description", "최초 설명")
        .containsEntry("business_hours", "09:00~18:00");
  }

  @Test
  void storesLongTourApiBusinessHoursWithoutTruncation() {
    insert("장문 운영시간 대상");
    Long placeId =
        jdbcTemplate.queryForObject(
            "SELECT place_id FROM places WHERE tour_content_id = 999999001", Long.class);
    String businessHours = "운".repeat(300);

    placeRepository.updateBusinessHoursIfMissing(placeId, businessHours);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT business_hours FROM places WHERE place_id = ?", String.class, placeId))
        .isEqualTo(businessHours);
  }

  private int insert(String name) {
    return placeRepository.insertTourApiPlace(
        999999001L,
        12,
        name,
        "자연 관광",
        "THINKER",
        "[\"자연 관광\"]",
        "광주광역시 북구",
        new BigDecimal("35.1"),
        new BigDecimal("126.8"),
        null);
  }
}
