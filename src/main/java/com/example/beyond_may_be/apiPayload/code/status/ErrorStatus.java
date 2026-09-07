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
  COURSE_INVALID_PLACE_COUNT(HttpStatus.BAD_REQUEST, "COURSE400", "여행 기간에 필요한 최소 장소 수를 충족하지 않습니다."),
  COURSE_DUPLICATE_PLACE(HttpStatus.BAD_REQUEST, "COURSE400_2", "중복된 장소가 포함되어 있습니다."),
  COURSE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE404_2", "선택한 장소를 찾을 수 없습니다."),
  COURSE_DAY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "COURSE400_4", "요청한 일자가 코스 여행 기간을 벗어났습니다."),
  COURSE_CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "COURSE400_5", "요청 메시지는 150자를 초과할 수 없습니다."),
  COURSE_AI_REVISION_LIMIT_EXCEEDED(
      HttpStatus.CONFLICT, "COURSE409_2", "AI 코스 수정 요청 횟수를 모두 사용했습니다. 직접 수정을 이용해주세요."),
  COURSE_AI_REQUEST_FAILED(
      HttpStatus.SERVICE_UNAVAILABLE, "COURSE503", "AI 코스 수정 요청이 실패했습니다. 잠시 후 다시 시도해주세요."),
  COURSE_GENERATION_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "COURSE503_2", "AI 코스 생성 시간이 초과되었습니다."),

  // Exploration
  EXPLORATION_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPLORATION404", "탐험을 찾을 수 없습니다."),
  EXPLORATION_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPLORATION404_2", "탐험 참여자를 찾을 수 없습니다."),
  EXPLORATION_PARTICIPANT_FORBIDDEN(HttpStatus.FORBIDDEN, "EXPLORATION403", "해당 탐험의 활성 참여자가 아닙니다."),
  SHARE_LINK_EXPIRED(HttpStatus.GONE, "EXPLORATION410", "공유 링크가 만료되었습니다."),
  DUPLICATE_ACTIVE_PARTICIPATION(HttpStatus.CONFLICT, "EXPLORATION409", "이미 다른 탐험에 참여 중입니다."),
  EXPLORATION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "EXPLORATION409_2", "이미 완료된 탐험입니다."),
  EXPLORATION_START_CONFLICT(HttpStatus.CONFLICT, "EXPLORATION409_3", "이미 시작했거나 완료된 탐험입니다."),
  EXPLORATION_NOT_ONGOING(HttpStatus.CONFLICT, "EXPLORATION409_4", "진행 중인 탐험이 아닙니다."),

  PLACE_DETAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE404", "장소를 찾을 수 없습니다."),
  PLACE_DETAIL_UNAVAILABLE(
      HttpStatus.SERVICE_UNAVAILABLE, "PLACE503", "장소 상세정보를 일시적으로 불러오지 못했습니다."),

  // Visit
  VISIT_NOT_FOUND(HttpStatus.NOT_FOUND, "VISIT404", "방문 기록을 찾을 수 없습니다."),
  VISIT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "VISIT400", "인증 반경을 벗어났습니다."),
  VISIT_PHOTO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "VISIT413", "방문 사진은 10MB를 초과할 수 없습니다."),
  VISIT_PHOTO_UNSUPPORTED_TYPE(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VISIT415", "JPEG, PNG 또는 WebP 형식의 방문 사진만 첨부할 수 있습니다."),
  VISIT_ALREADY_EXISTS(HttpStatus.CONFLICT, "VISIT409", "이미 방문 인증한 장소입니다."),
  VISIT_PHOTO_ORDER_CONFLICT(HttpStatus.CONFLICT, "VISIT409_2", "방문 사진 표시 순서가 충돌했습니다. 다시 시도해주세요."),

  // Recommendation
  RECOMMENDATION_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "RECOMMENDATION400", "여행 기간이 올바르지 않습니다."),
  RECOMMENDATION_INVALID_REACTIONS(
      HttpStatus.BAD_REQUEST, "RECOMMENDATION400_2", "추천 회차 반응이 올바르지 않습니다."),
  RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOMMENDATION404", "현재 추천을 찾을 수 없습니다."),
  RECOMMENDATION_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOMMENDATION404_2", "추천 회차를 찾을 수 없습니다."),
  RECOMMENDATION_PREFERENCE_REQUIRED(
      HttpStatus.CONFLICT, "RECOMMENDATION409", "완료된 여행 성향 검사가 필요합니다."),
  RECOMMENDATION_BATCH_CONFLICT(
      HttpStatus.CONFLICT, "RECOMMENDATION409_2", "요청한 추천 회차 구성이 변경되었습니다."),
  RECOMMENDATION_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMENDATION503", "추천 생성 시간이 초과되었습니다.");

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
