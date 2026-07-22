package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PostgreSqlImageConsistencyTest {

  private static final Pattern POSTGRES_IMAGE_PATTERN =
      Pattern.compile("(?m)^\\s*image:\\s*(postgres:[^\\s]+)\\s*$");

  @Test
  void composeAndTestcontainersUseTheSameDigestPinnedPostgresImage()
      throws IOException, NoSuchFieldException, IllegalAccessException {
    String composeContents = Files.readString(Path.of("docker-compose.yml"));
    Matcher matcher = POSTGRES_IMAGE_PATTERN.matcher(composeContents);

    assertTrue(matcher.find(), "docker-compose.yml에 PostgreSQL image가 있어야 합니다.");

    String composeImage = matcher.group(1);
    Field imageField = TestcontainersConfiguration.class.getDeclaredField("POSTGRES_IMAGE");
    String testcontainersImage = (String) imageField.get(null);

    assertTrue(composeImage.contains("@sha256:"), "Compose image는 digest로 고정해야 합니다.");
    assertTrue(testcontainersImage.contains("@sha256:"), "Testcontainers image는 digest로 고정해야 합니다.");
    assertEquals(composeImage, testcontainersImage);
  }
}
