package com.example.beyond_may_be.preference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.preference.domain.Question;
import com.example.beyond_may_be.preference.domain.QuestionOption;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.preference.repository.QuestionOptionRepository;
import com.example.beyond_may_be.preference.repository.QuestionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferenceTestServiceTest {

  @InjectMocks private PreferenceTestService preferenceTestService;

  @Mock private QuestionRepository questionRepository;

  @Mock private QuestionOptionRepository questionOptionRepository;

  @DisplayName("활성 질문과 각 질문의 선택지를 순서대로 조회해 응답으로 변환한다.")
  @Test
  void getQuestions() {
    // given
    Question question1 = Question.builder().content("질문1").active(true).build();
    Question question2 = Question.builder().content("질문2").active(true).build();

    QuestionOption option1 =
        QuestionOption.builder()
            .questionId(1L)
            .displayOrder(1)
            .content("선택지A")
            .thinkerWeight(1)
            .foodieWeight(0)
            .artistWeight(0)
            .remembererWeight(0)
            .build();
    QuestionOption option2 =
        QuestionOption.builder()
            .questionId(2L)
            .displayOrder(1)
            .content("선택지B")
            .thinkerWeight(0)
            .foodieWeight(1)
            .artistWeight(0)
            .remembererWeight(0)
            .build();

    given(questionRepository.findByActiveTrue()).willReturn(List.of(question1, question2));
    given(questionOptionRepository.findByQuestionIdInOrderByQuestionIdAscDisplayOrderAsc(anyList()))
        .willReturn(List.of(option1, option2));

    // when
    QuestionsResponse response = preferenceTestService.getQuestions();

    // then
    assertThat(response.questions()).hasSize(2);
    assertThat(response.questions().get(0).content()).isEqualTo("질문1");
    assertThat(response.questions().get(1).content()).isEqualTo("질문2");
  }
}
