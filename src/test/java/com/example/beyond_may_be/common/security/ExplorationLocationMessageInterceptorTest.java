package com.example.beyond_may_be.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationLocationService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ExplorationLocationMessageInterceptorTest {

  @Mock private ExplorationLocationService locationService;
  @Mock private MessageChannel channel;

  private ExplorationLocationMessageInterceptor interceptor;
  private ValidatorFactory validatorFactory;

  @BeforeEach
  void setUp() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    interceptor =
        new ExplorationLocationMessageInterceptor(
            JsonMapper.builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build(),
            validatorFactory.getValidator(),
            locationService);
  }

  @AfterEach
  void tearDown() {
    validatorFactory.close();
  }

  @Test
  void validLocationSendIsAcceptedAndConsumed() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    Message<?> result = interceptor.preSend(message, channel);

    assertThat(result).isNull();
    then(locationService)
        .should()
        .accept(
            "session-1",
            44L,
            7L,
            new ExplorationDtos.LocationUpdateRequest(
                new java.math.BigDecimal("35.1402"),
                new java.math.BigDecimal("126.9124"),
                new java.math.BigDecimal("18.5"),
                OffsetDateTime.parse("2026-08-15T14:34:59+09:00")));
  }

  @Test
  void outOfRangeLatitudeIsRejected() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 90.0001,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertRejected(message, Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void stringCoordinateIsRejected() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": "35.1402",
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertRejected(message, Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void unknownFieldIsRejected() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00",
              "participantId": 72
            }
            """);

    assertRejected(message, Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void nonJsonContentTypeIsRejected() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """,
            MimeTypeUtils.TEXT_PLAIN);

    assertRejected(message, Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void invalidRecordedAtIsRejected() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59"
            }
            """);

    assertRejected(message, Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void nullJsonPayloadIsRejected() {
    assertRejected(locationMessage("null"), Code.LOCATION_PAYLOAD_INVALID);
  }

  @Test
  void excessiveAccuracyHasDedicatedErrorCode() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 50.1,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertRejected(message, Code.LOCATION_ACCURACY_EXCEEDED);
  }

  @Test
  void jsonContentTypeWithParametersIsAccepted() {
    Message<byte[]> message =
        locationMessage(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """,
            MimeType.valueOf("application/json;charset=UTF-8"));

    assertThat(interceptor.preSend(message, channel)).isNull();
  }

  private void assertRejected(Message<byte[]> message, Code code) {
    assertThatThrownBy(() -> interceptor.preSend(message, channel))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
    then(locationService).shouldHaveNoInteractions();
  }

  private Message<byte[]> locationMessage(String payload) {
    return locationMessage(payload, MimeTypeUtils.APPLICATION_JSON);
  }

  private Message<byte[]> locationMessage(String payload, MimeType contentType) {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/explorations/44/locations");
    headers.setSessionId("session-1");
    headers.setNativeHeader("content-type", contentType.toString());
    headers.setContentType(contentType);
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    return MessageBuilder.createMessage(
        payload.getBytes(StandardCharsets.UTF_8), headers.getMessageHeaders());
  }
}
