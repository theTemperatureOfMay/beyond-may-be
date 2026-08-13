import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ["rate>0.99999"],
  },
};

const healthUrl = `${__ENV.TARGET_BASE_URL}/actuator/health`;

export default function () {
  let response = null;
  let status = null;

  for (let attempt = 0; attempt < 60; attempt += 1) {
    response = http.get(healthUrl, { timeout: "2s" });
    try {
      status = response.json("status");
    } catch (_) {
      status = null;
    }

    if (response.status === 200 && status === "UP") {
      break;
    }
    sleep(2);
  }

  check(response, {
    "health가 HTTP 200을 반환한다": (result) => result !== null && result.status === 200,
    "애플리케이션 상태가 UP이다": () => status === "UP",
  });
}
