package com.example.beyond_may_be.question.dto;

import java.util.List;
import java.util.Map;

public final class PreferenceTestDtos {
  private PreferenceTestDtos() {}

  public record ResultRequest(List<AnswerRequest> answers) {}

  public record AnswerRequest(String questionId, Integer selectedIndex) {}

  public record QuestionsResponse(List<QuestionResponse> questions, int totalCount) {}

  public record QuestionResponse(
      String questionId, int order, String content, List<String> options) {}

  public record ResultResponse(
      String resultToken,
      String preferenceType,
      String description,
      Map<String, Integer> scoreDetail) {}
}
