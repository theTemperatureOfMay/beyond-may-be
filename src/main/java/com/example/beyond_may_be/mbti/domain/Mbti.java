package com.example.beyond_may_be.mbti.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
@Table(name = "mbti")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mbti extends BaseEntity {

  @Id
  @Column(name = "mbti_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "mbti_name", nullable = false, length = 10)
  private String mbtiName;

  @ElementCollection
  @CollectionTable(name = "mbti_tags", joinColumns = @JoinColumn(name = "mbti_id"))
  @Column(name = "mbti_tag", nullable = false)
  private List<String> mbtiTags = new ArrayList<>();

  @Column(name = "mbti_img", nullable = false)
  private String mbtiImg;
}
