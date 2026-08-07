package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PlaceBasedVisitMigrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

  @Test
  void migratesExistingCoursePlaceVisitToPlaceVisit() throws SQLException {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("1")
        .load()
        .migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO course_places
            (course_place_id, created_at, updated_at, course_id, day_number,
             estimated_stay_minutes, place_id, visit_order)
          VALUES (11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 7, 1, 60, 101, 1)
          """);
      statement.executeUpdate(
          """
          INSERT INTO visits
            (visit_id, created_at, updated_at, course_place_id, participant_id, visited_at)
          VALUES (13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 11, 17, CURRENT_TIMESTAMP)
          """);
    }

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      ResultSet migrated =
          statement.executeQuery(
              "SELECT place_id, course_place_id FROM visits WHERE visit_id = 13");
      migrated.next();
      assertEquals(101L, migrated.getLong("place_id"));
      assertEquals(11L, migrated.getLong("course_place_id"));

      statement.executeUpdate(
          """
          INSERT INTO visits
            (created_at, updated_at, place_id, course_place_id, participant_id, visited_at)
          VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 102, NULL, 17, CURRENT_TIMESTAMP)
          """);
      ResultSet nearby =
          statement.executeQuery(
              "SELECT course_place_id FROM visits WHERE participant_id = 17 AND place_id = 102");
      nearby.next();
      assertNull(nearby.getObject("course_place_id"));

      assertThrows(
          SQLException.class,
          () ->
              statement.executeUpdate(
                  """
                  INSERT INTO visits
                    (created_at, updated_at, place_id, participant_id, visited_at)
                  VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 102, 17, CURRENT_TIMESTAMP)
                  """));
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
