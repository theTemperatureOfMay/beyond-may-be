package com.example.beyond_may_be.preference.dto;

import java.util.List;

public final class PreferenceTestDtos {
  private PreferenceTestDtos() {}

  public record QuestionsResponse(List<QuestionResponse> questions) {}

  public record QuestionResponse(
      Long questionId, String content, List<QuestionOptionResponse> options) {}

  public record QuestionOptionResponse(
      Long optionId,
      Integer displayOrder,
      String content,
      Integer thinkerWeight,
      Integer foodieWeight,
      Integer artistWeight,
      Integer remembererWeight) {}
}
