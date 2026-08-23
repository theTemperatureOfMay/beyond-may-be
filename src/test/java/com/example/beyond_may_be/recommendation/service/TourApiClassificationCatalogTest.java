package com.example.beyond_may_be.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TourApiClassificationCatalogTest {

  @Test
  void usesTheMostSpecificKnownClassificationName() {
    assertThat(TourApiClassificationCatalog.resolve("VE", "VE07", "VE070100"))
        .satisfies(
            classification -> {
              assertThat(classification.category()).isEqualTo("박물관");
              assertThat(classification.tags()).containsExactly("문화관광", "전시시설", "박물관");
            });

    assertThat(TourApiClassificationCatalog.resolve("VE", "VE07", "VE999999"))
        .satisfies(
            classification -> {
              assertThat(classification.category()).isEqualTo("전시시설");
              assertThat(classification.tags()).containsExactly("문화관광", "전시시설");
            });

    assertThat(TourApiClassificationCatalog.resolve("VE", "VE99", "VE999999"))
        .satisfies(
            classification -> {
              assertThat(classification.category()).isEqualTo("문화관광");
              assertThat(classification.tags()).containsExactly("문화관광");
            });

    assertThat(TourApiClassificationCatalog.resolve("VE", null, null))
        .satisfies(
            classification -> {
              assertThat(classification.category()).isEqualTo("문화관광");
              assertThat(classification.tags()).containsExactly("문화관광");
            });
  }
}
