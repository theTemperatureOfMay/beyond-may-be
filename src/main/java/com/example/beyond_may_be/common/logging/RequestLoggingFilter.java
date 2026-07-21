package com.example.beyond_may_be.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String HEALTH_ENDPOINT = "/actuator/health";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return (request.getContextPath() + HEALTH_ENDPOINT).equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    long startTime = System.nanoTime();

    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
      Object bestMatchingPattern =
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String route = bestMatchingPattern instanceof String pattern ? pattern : "unmatched";

      log.info(
          "http_request method={} route={} status={} duration_ms={}",
          request.getMethod(),
          route,
          response.getStatus(),
          durationMillis);
      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }
}
