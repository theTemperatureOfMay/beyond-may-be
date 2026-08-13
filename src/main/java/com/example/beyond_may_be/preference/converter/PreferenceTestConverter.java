package com.example.beyond_may_be.preference.converter;

import com.example.beyond_may_be.preference.domain.Question;
import com.example.beyond_may_be.preference.domain.QuestionOption;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionOptionResponse;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionResponse;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionsResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PreferenceTestConverter {
  private PreferenceTestConverter() {}

  public static QuestionsResponse toQuestionsResponse(
      List<Question> questions, Map<Long, List<QuestionOption>> optionsByQuestionId) {
    List<QuestionResponse> questionResponses =
        questions.stream()
            .map(
                question ->
                    toQuestionResponse(
                        question, optionsByQuestionId.getOrDefault(question.getId(), List.of())))
            .collect(Collectors.toList());
    return new QuestionsResponse(questionResponses);
  }

  private static QuestionResponse toQuestionResponse(
      Question question, List<QuestionOption> options) {
    List<QuestionOptionResponse> optionResponses =
        options.stream().map(PreferenceTestConverter::toQuestionOptionResponse).toList();
    return new QuestionResponse(question.getId(), question.getContent(), optionResponses);
  }

  private static QuestionOptionResponse toQuestionOptionResponse(QuestionOption option) {
    return new QuestionOptionResponse(
        option.getId(),
        option.getDisplayOrder(),
        option.getContent(),
        option.getThinkerWeight(),
        option.getFoodieWeight(),
        option.getArtistWeight(),
        option.getRemembererWeight());
  }
}
