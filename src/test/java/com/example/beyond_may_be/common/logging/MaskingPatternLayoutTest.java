package com.example.beyond_may_be.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaskingPatternLayoutTest {

  private LoggerContext loggerContext;
  private MaskingPatternLayout layout;

  @BeforeEach
  void setUp() {
    loggerContext = new LoggerContext();
    layout = new MaskingPatternLayout();
    layout.setContext(loggerContext);
    layout.setPattern("%msg");
    layout.start();
  }

  @AfterEach
  void tearDown() {
    layout.stop();
    loggerContext.stop();
  }

  @Test
  void masksSensitiveKeyValues() {
    assertMasked("password=plain-secret", "plain-secret");
    assertMasked("token: plain-secret", "plain-secret");
    assertMasked("apiKey=\"plain-secret\"", "plain-secret");
    assertMasked("email=user@example.com", "user@example.com");
    assertMasked("phone=010-1234-5678", "010-1234-5678");
    assertMasked("nickname=traveler", "traveler");
    assertMasked("{\"password\":\"plain-secret\"}", "plain-secret");
  }

  @Test
  void masksAuthorizationBearerAndJwtValues() {
    assertMasked("Authorization: Bearer abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz");
    assertMasked("credential=Bearer abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz");
    assertMasked(
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature",
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature");
  }

  @Test
  void leavesOrdinaryAccessLogUnchanged() {
    String message = "http_request method=GET route=/api/v1/courses status=200 duration_ms=12";

    assertEquals(message, format(message));
  }

  private void assertMasked(String message, String sensitiveValue) {
    String masked = format(message);

    assertFalse(masked.contains(sensitiveValue));
    assertTrue(masked.contains("***"));
  }

  private String format(String message) {
    LoggingEvent event = new LoggingEvent();
    event.setMessage(message);
    return layout.doLayout(event);
  }
}
