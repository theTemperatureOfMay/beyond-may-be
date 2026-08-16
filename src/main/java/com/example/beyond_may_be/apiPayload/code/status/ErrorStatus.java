package com.example.beyond_may_be.apiPayload.code.status;

import com.example.beyond_may_be.apiPayload.code.BaseErrorCode;
import com.example.beyond_may_be.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

  // For test
  TEMP_EXCEPTION(HttpStatus.BAD_REQUEST, "TEMP4001", "이거는 테스트"),

  // 가장 일반적인 응답
  _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
  _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
  _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
  _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

  // User
  USER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "USER401", "닉네임 또는 식별 코드가 올바르지 않습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "사용자를 찾을 수 없습니다."),

  // Course
  COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE404", "코스를 찾을 수 없습니다."),
  COURSE_FORBIDDEN(HttpStatus.FORBIDDEN, "COURSE403", "코스 소유자만 수행할 수 있습니다."),
  COURSE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "COURSE409", "이미 확정된 코스입니다."),

  // Exploration
  EXPLORATION_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPLORATION404", "탐험을 찾을 수 없습니다."),
  SHARE_LINK_EXPIRED(HttpStatus.GONE, "EXPLORATION410", "공유 링크가 만료되었습니다."),
  DUPLICATE_ACTIVE_PARTICIPATION(HttpStatus.CONFLICT, "EXPLORATION409", "이미 다른 탐험에 참여 중입니다."),
  PARTICIPANT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "EXPLORATION403", "활성 참여자만 수행할 수 있습니다."),
  EXPLORATION_ALREADY_STARTED(HttpStatus.CONFLICT, "EXPLORATION409_2", "이미 시작된 탐험입니다."),

  // Visit
  PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "VISIT404", "장소를 찾을 수 없습니다."),
  VISIT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "VISIT400", "인증 반경을 벗어났습니다."),
  VISIT_ALREADY_EXISTS(HttpStatus.CONFLICT, "VISIT409", "이미 방문 인증한 장소입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;

  @Override
  public ErrorReasonDTO getReason() {
    return ErrorReasonDTO.builder().message(message).code(code).isSuccess(false).build();
  }

  @Override
  public ErrorReasonDTO getReasonHttpStatus() {
    return ErrorReasonDTO.builder()
        .message(message)
        .code(code)
        .isSuccess(false)
        .httpStatus(httpStatus)
        .build();
  }
}
