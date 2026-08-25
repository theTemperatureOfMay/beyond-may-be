package com.example.beyond_may_be.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class TourApiSyncClientTest {

  private MockRestServiceServer server;
  private TourApiSyncClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new TourApiSyncClient(builder.build(), "test-key");
  }

  @Test
  void requestsOneGwangjuChangePageAndMapsItems() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.containsString("/areaBasedSyncList2")))
        .andExpect(queryParam("serviceKey", "test-key"))
        .andExpect(queryParam("numOfRows", "200"))
        .andExpect(queryParam("pageNo", "1"))
        .andExpect(queryParam("MobileOS", "ETC"))
        .andExpect(queryParam("MobileApp", "BeyondMayBe"))
        .andExpect(queryParam("_type", "json"))
        .andExpect(queryParam("showflag", "1"))
        .andExpect(queryParam("modifiedtime", "20260821"))
        .andExpect(queryParam("areaCode", "5"))
        .andExpect(queryParam("lDongRegnCd", "12"))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
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
                          "showflag": "1",
                          "title": "새 문화 공간",
                          "lclsSystm1": "VE",
                          "lclsSystm2": "VE07",
                          "lclsSystm3": "VE070100"
                        }]
                      },
                      "numOfRows": 200,
                      "pageNo": 1,
                      "totalCount": 1
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    List<TourApiSyncClient.SyncItem> items = client.fetchChangedPlaces();

    assertThat(items).hasSize(1);
    assertThat(items.getFirst().contentId()).isEqualTo("999001");
    assertThat(items.getFirst().title()).isEqualTo("새 문화 공간");
    assertThat(items.getFirst().largeCategoryCode()).isEqualTo("VE");
    server.verify();
  }

  @Test
  void encodesDecodedServiceKeyOnce() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    MockRestServiceServer encodedServer = MockRestServiceServer.bindTo(builder).build();
    TourApiSyncClient encodedClient = new TourApiSyncClient(builder.build(), "abc+def/ghi=");
    encodedServer
        .expect(
            once(),
            requestTo(org.hamcrest.Matchers.containsString("serviceKey=abc%2Bdef%2Fghi%3D")))
        .andRespond(withRawStatus(500));

    assertThat(encodedClient.fetchChangedPlaces()).isEmpty();
    encodedServer.verify();
  }

  @Test
  void returnsEmptyListForErrorResponse() {
    server
        .expect(requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withRawStatus(500));

    assertThat(client.fetchChangedPlaces()).isEmpty();
    server.verify();
  }

  @Test
  void returnsEmptyListForMalformedJson() {
    server
        .expect(requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

    assertThat(client.fetchChangedPlaces()).isEmpty();
    server.verify();
  }

  @Test
  void returnsEmptyListForTourApiErrorCode() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            withSuccess(
                """
                {"response":{"header":{"resultCode":"99","resultMsg":"ERROR"}}}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchChangedPlaces()).isEmpty();
    server.verify();
  }

  @Test
  void returnsEmptyListForTimeoutWithoutRetrying() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            request -> {
              throw new ResourceAccessException("timeout");
            });

    assertThat(client.fetchChangedPlaces()).isEmpty();
    server.verify();
  }
}
