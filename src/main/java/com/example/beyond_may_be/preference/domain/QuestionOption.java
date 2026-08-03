package com.example.beyond_may_be.preference.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "question_options",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"question_id", "display_order"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionOption extends BaseEntity {

  @Id
  @Column(name = "question_option_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "question_id", nullable = false)
  private Long questionId;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Column(nullable = false)
  private String content;

  @Column(name = "thinker_weight", nullable = false)
  private Integer thinkerWeight;

  @Column(name = "foodie_weight", nullable = false)
  private Integer foodieWeight;

  @Column(name = "artist_weight", nullable = false)
  private Integer artistWeight;

  @Column(name = "rememberer_weight", nullable = false)
  private Integer remembererWeight;

  @Builder
  public QuestionOption(
      Long questionId,
      Integer displayOrder,
      String content,
      Integer thinkerWeight,
      Integer foodieWeight,
      Integer artistWeight,
      Integer remembererWeight) {
    this.questionId = questionId;
    this.displayOrder = displayOrder;
    this.content = content;
    this.thinkerWeight = thinkerWeight;
    this.foodieWeight = foodieWeight;
    this.artistWeight = artistWeight;
    this.remembererWeight = remembererWeight;
  }
}
