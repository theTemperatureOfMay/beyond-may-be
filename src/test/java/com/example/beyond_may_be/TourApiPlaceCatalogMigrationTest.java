package com.example.beyond_may_be;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TourApiPlaceCatalogMigrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres"));

  @BeforeEach
  void resetSchema() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA public CASCADE");
      statement.execute("CREATE SCHEMA public");
    }
  }

  @Test
  void migratesTourApiPlaceCatalog() throws SQLException {
    flyway().migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(171, queryLong(statement, "SELECT COUNT(*) FROM public.places"));
      assertEquals(
          171, queryLong(statement, "SELECT COUNT(DISTINCT tour_content_id) FROM public.places"));
      assertEquals(
          171,
          queryLong(
              statement,
              "SELECT COUNT(*) FROM public.places WHERE tour_content_type_id IS NOT NULL"));
      assertEquals(
          0,
          queryLong(
              statement, "SELECT COUNT(*) FROM public.places WHERE tour_content_type_id = 32"));

      assertEquals(
          Map.of("THINKER", 19L, "FOODIE", 84L, "ARTIST", 36L, "REMEMBERER", 32L),
          placeCountsByType(statement));
      assertTrue(columnIsNullable(statement, "tour_content_id"));
      assertTrue(columnIsNullable(statement, "tour_content_type_id"));

      statement.executeUpdate(manualPlaceSql("수동 장소 1", "NULL"));
      statement.executeUpdate(manualPlaceSql("수동 장소 2", "NULL"));
      assertThrows(
          SQLException.class, () -> statement.executeUpdate(manualPlaceSql("중복 장소", "126385")));
    }
  }

  @Test
  void seedKeepsExistingTourApiPlace() throws SQLException {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("7")
        .load()
        .migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(manualPlaceSql("기존 검수 장소", "126385"));
    }

    flyway().migrate();

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      assertEquals(171, queryLong(statement, "SELECT COUNT(*) FROM public.places"));
      try (ResultSet resultSet =
          statement.executeQuery("SELECT name FROM public.places WHERE tour_content_id = 126385")) {
        resultSet.next();
        assertEquals("기존 검수 장소", resultSet.getString("name"));
      }
    }
  }

  private static Flyway flyway() {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load();
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static long queryLong(Statement statement, String sql) throws SQLException {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static Map<String, Long> placeCountsByType(Statement statement) throws SQLException {
    Map<String, Long> counts = new HashMap<>();
    try (ResultSet resultSet =
        statement.executeQuery(
            "SELECT travel_mbti_type, COUNT(*) FROM public.places GROUP BY travel_mbti_type")) {
      while (resultSet.next()) {
        counts.put(resultSet.getString(1), resultSet.getLong(2));
      }
    }
    return counts;
  }

  private static boolean columnIsNullable(Statement statement, String columnName)
      throws SQLException {
    try (ResultSet resultSet =
        statement.executeQuery(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'places' "
                + "AND column_name = '"
                + columnName
                + "'")) {
      resultSet.next();
      return "YES".equals(resultSet.getString(1));
    }
  }

  private static String manualPlaceSql(String name, String tourContentId) {
    return """
        INSERT INTO public.places (
            created_at, updated_at, active, address, category, latitude, longitude,
            name, tags, travel_mbti_type, tour_content_id, tour_content_type_id
        ) VALUES (
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true, '광주광역시', '역사관광',
            35.100000, 126.800000, '%s', '[]'::jsonb, 'REMEMBERER', %s, 12
        )
        """
        .formatted(name, tourContentId);
  }
}
