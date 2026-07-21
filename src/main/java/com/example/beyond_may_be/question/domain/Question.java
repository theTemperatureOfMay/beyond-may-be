package com.example.beyond_may_be.question.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.mbti.domain.enums.MbtiType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseEntity {

  @Id
  @Column(name = "question_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "question_content", nullable = false, length = 200)
  private String questionContent;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_category", nullable = false)
  private MbtiType questionCategory;

  @ElementCollection
  @CollectionTable(name = "question_lists", joinColumns = @JoinColumn(name = "question_id"))
  @Column(name = "question_list", nullable = false)
  private List<String> questionList = new ArrayList<>();
}
