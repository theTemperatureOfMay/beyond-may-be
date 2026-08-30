package com.example.beyond_may_be.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TourApiNearbyPlaceClientTest {

  private MockRestServiceServer server;
  private TourApiNearbyPlaceClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new TourApiNearbyPlaceClient(builder.build(), "test-key");
  }

  @Test
  void requestsNearbyPlacesWithinOneKilometerInDistanceOrder() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.containsString("/locationBasedList2")))
        .andExpect(queryParam("serviceKey", "test-key"))
        .andExpect(queryParam("numOfRows", "20"))
        .andExpect(queryParam("pageNo", "1"))
        .andExpect(queryParam("MobileOS", "ETC"))
        .andExpect(queryParam("MobileApp", "BeyondMayBe"))
        .andExpect(queryParam("_type", "json"))
        .andExpect(queryParam("mapX", "126.919900"))
        .andExpect(queryParam("mapY", "35.146600"))
        .andExpect(queryParam("radius", "1000"))
        .andExpect(queryParam("arrange", "E"))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {"resultCode": "0000"},
                    "body": {
                      "items": {
                        "item": [{
                          "addr1": "광주광역시 동구 문화전당로 38",
                          "addr2": "",
                          "contentid": "999001",
                          "contenttypeid": "14",
                          "firstimage": "https://example.test/place.jpg",
                          "mapx": "126.919998",
                          "mapy": "35.146702",
                          "areacode": "5",
                          "title": "새 문화 공간",
                          "lclsSystm1": "VE",
                          "lclsSystm2": "VE07",
                          "lclsSystm3": "VE070100"
                        }]
                      }
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    List<TourApiNearbyPlaceClient.NearbyItem> items =
        client.fetchNearbyPlaces(new BigDecimal("35.146600"), new BigDecimal("126.919900"));

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().contentId()).isEqualTo("999001");
    assertThat(items.getFirst().areaCode()).isEqualTo("5");
    assertThat(items.getFirst().largeCategoryCode()).isEqualTo("VE");
    server.verify();
  }

  @Test
  void returnsEmptyListWhenTourApiFailsWithoutRetrying() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withRawStatus(500));

    assertThat(client.fetchNearbyPlaces(new BigDecimal("35.146600"), new BigDecimal("126.919900")))
        .isEmpty();
    server.verify();
  }
}
