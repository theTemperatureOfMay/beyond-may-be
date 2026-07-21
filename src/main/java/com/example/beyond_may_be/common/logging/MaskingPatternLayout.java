package com.example.beyond_may_be.common.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import java.util.regex.Pattern;

public class MaskingPatternLayout extends PatternLayout {

  private static final String SENSITIVE_KEY =
      "password|passwd|pwd|token|access[_-]?token|refresh[_-]?token|id[_-]?token"
          + "|authorization|cookie|set-cookie|api[_-]?key|apikey|secret"
          + "|email|phone|mobile|tel|nickname|username";

  private static final List<Pattern> MASKING_PATTERNS =
      List.of(
          Pattern.compile(
              "(?im)(\\b(?:authorization|cookie|set-cookie)\\b\\s*[:=]\\s*)([^\\r\\n]+)"),
          Pattern.compile(
              "(?i)([\\\"']?\\b(?:"
                  + SENSITIVE_KEY
                  + ")\\b[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,}\\]]+)"),
          Pattern.compile("(?i)(Bearer\\s+)([A-Za-z0-9._~+\\/-]+=*)"),
          Pattern.compile(
              "(?m)(^|[^A-Za-z0-9_-])(eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)"));

  @Override
  public String doLayout(ILoggingEvent event) {
    String masked = super.doLayout(event);

    for (Pattern pattern : MASKING_PATTERNS) {
      masked = pattern.matcher(masked).replaceAll("$1***");
    }

    return masked;
  }
}
