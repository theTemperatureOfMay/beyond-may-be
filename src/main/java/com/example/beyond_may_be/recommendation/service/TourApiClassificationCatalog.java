package com.example.beyond_may_be.recommendation.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TourApiClassificationCatalog {

  private static final String RESOURCE = "/tourapi/classification.psv";
  private static final Catalog CATALOG = load();

  private TourApiClassificationCatalog() {}

  static Classification resolve(String largeCode, String middleCode, String smallCode) {
    Entry small = smallCode == null ? null : CATALOG.smallEntries().get(smallCode);
    if (small != null
        && small.largeCode().equals(largeCode)
        && (middleCode == null || middleCode.isBlank() || small.middleCode().equals(middleCode))) {
      return new Classification(
          small.smallName(), List.of(small.largeName(), small.middleName(), small.smallName()));
    }

    Entry middle = middleCode == null ? null : CATALOG.middleEntries().get(middleCode);
    if (middle != null && middle.largeCode().equals(largeCode)) {
      return new Classification(
          middle.middleName(), List.of(middle.largeName(), middle.middleName()));
    }

    String largeName = largeCode == null ? null : CATALOG.largeNames().get(largeCode);
    if (largeName == null) {
      throw new IllegalArgumentException("지원하지 않는 TourAPI 대분류입니다.");
    }
    return new Classification(largeName, List.of(largeName));
  }

  private static Catalog load() {
    Map<String, String> largeNames = new LinkedHashMap<>();
    Map<String, Entry> middleEntries = new LinkedHashMap<>();
    Map<String, Entry> smallEntries = new LinkedHashMap<>();
    try (InputStream input = TourApiClassificationCatalog.class.getResourceAsStream(RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("TourAPI 신분류체계 로컬 매핑을 찾을 수 없습니다.");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        reader
            .lines()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .map(TourApiClassificationCatalog::parse)
            .forEach(
                entry -> {
                  largeNames.putIfAbsent(entry.largeCode(), entry.largeName());
                  middleEntries.putIfAbsent(entry.middleCode(), entry);
                  smallEntries.put(entry.smallCode(), entry);
                });
      }
    } catch (IOException exception) {
      throw new IllegalStateException("TourAPI 신분류체계 로컬 매핑을 읽을 수 없습니다.", exception);
    }
    return new Catalog(Map.copyOf(largeNames), Map.copyOf(middleEntries), Map.copyOf(smallEntries));
  }

  private static Entry parse(String line) {
    String[] values = line.split("\\|", -1);
    if (values.length != 6) {
      throw new IllegalStateException("TourAPI 신분류체계 로컬 매핑 형식이 올바르지 않습니다.");
    }
    return new Entry(values[0], values[1], values[2], values[3], values[4], values[5]);
  }

  record Classification(String category, List<String> tags) {}

  private record Catalog(
      Map<String, String> largeNames,
      Map<String, Entry> middleEntries,
      Map<String, Entry> smallEntries) {}

  private record Entry(
      String largeCode,
      String largeName,
      String middleCode,
      String middleName,
      String smallCode,
      String smallName) {}
}
