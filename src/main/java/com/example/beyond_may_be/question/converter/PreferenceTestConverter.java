package com.example.beyond_may_be.question.converter;

import com.example.beyond_may_be.mbti.domain.enums.MbtiType;
import com.example.beyond_may_be.question.domain.Question;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.QuestionResponse;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class PreferenceTestConverter {

  private PreferenceTestConverter() {}

  public static QuestionsResponse toQuestionsResponse(List<Question> questions) {
    List<QuestionResponse> questionResponses =
        IntStream.range(0, questions.size())
            .mapToObj(index -> toQuestionResponse(questions.get(index), index + 1))
            .toList();

    return new QuestionsResponse(questionResponses, questionResponses.size());
  }

  public static ResultResponse toResultResponse(
      String resultToken,
      MbtiType preferenceType,
      String description,
      Map<MbtiType, Integer> scoreDetail) {
    return new ResultResponse(
        resultToken, toPreferenceTypeName(preferenceType), description, toScoreDetail(scoreDetail));
  }

  private static QuestionResponse toQuestionResponse(Question question, int order) {
    return new QuestionResponse(
        String.valueOf(question.getId()),
        order,
        question.getQuestionContent(),
        new ArrayList<>(question.getQuestionList()));
  }

  private static Map<String, Integer> toScoreDetail(Map<MbtiType, Integer> scoreDetail) {
    Map<String, Integer> convertedScoreDetail = new LinkedHashMap<>();
    for (MbtiType type : MbtiType.values()) {
      convertedScoreDetail.put(toPreferenceTypeName(type), scoreDetail.getOrDefault(type, 0));
    }
    return convertedScoreDetail;
  }

  private static String toPreferenceTypeName(MbtiType preferenceType) {
    return switch (preferenceType) {
      case THINKER -> "\uC0AC\uC0C9\uB7EC";
      case FOODIE -> "\uBBF8\uC2DD\uB7EC";
      case ARTIST -> "\uC608\uC220\uB7EC";
      case REMEMBERER -> "\uAE30\uC5B5\uB7EC";
    };
  }
}
