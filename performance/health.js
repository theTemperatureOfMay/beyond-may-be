import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ["rate>0.99999"],
  },
};

const appHealthUrl = `${__ENV.TARGET_BASE_URL}/actuator/health`;
const prometheusReadyUrl = "http://prometheus:9090/-/ready";
const grafanaHealthUrl = "http://grafana:3000/api/health";

function waitFor(url, responseCheck) {
  let response = null;
  for (let attempt = 0; attempt < 90; attempt += 1) {
    response = http.get(url, { timeout: "2s" });
    if (responseCheck(response)) {
      return response;
    }
    sleep(2);
  }
  return response;
}

export default function () {
  let appStatus = null;
  const appResponse = waitFor(appHealthUrl, (response) => {
    try {
      appStatus = response.json("status");
    } catch (_) {
      appStatus = null;
    }
    return response.status === 200 && appStatus === "UP";
  });
  const prometheusResponse = waitFor(prometheusReadyUrl, (response) => response.status === 200);
  const grafanaResponse = waitFor(grafanaHealthUrl, (response) => response.status === 200);

  check(appResponse, {
    "health가 HTTP 200을 반환한다": (result) => result !== null && result.status === 200,
    "애플리케이션 상태가 UP이다": () => appStatus === "UP",
    "Prometheus가 준비됐다": () => prometheusResponse?.status === 200,
    "Grafana가 준비됐다": () => grafanaResponse?.status === 200,
  });
}
