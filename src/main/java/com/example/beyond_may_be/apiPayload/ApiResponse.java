package com.example.beyond_may_be.apiPayload;

import com.example.beyond_may_be.apiPayload.code.BaseCode;
import com.example.beyond_may_be.apiPayload.code.status.SuccessStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"message", "code", "data", "success"})
public class ApiResponse<T> {

  private final String message;
  private final String code;
  private final T data;
  private final boolean success;

  // 성공한 경우 응답 생성

  public static <T> ApiResponse<T> onSuccess(T data) {
    return new ApiResponse<>(
        SuccessStatus._OK.getMessage(), SuccessStatus._OK.getCode(), data, true);
  }

  public static <T> ApiResponse<T> of(BaseCode code, T data) {
    return new ApiResponse<>(
        code.getReasonHttpStatus().getMessage(), code.getReasonHttpStatus().getCode(), data, true);
  }

  // 실패한 경우 응답 생성
  public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
    return new ApiResponse<>(message, code, data, false);
  }
}
