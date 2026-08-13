import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-arrival-rate",
      rate: 1,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 5,
      maxVUs: 100,
    },
  },
  thresholds: {
    checks: ["rate>0.99999"],
    http_req_failed: ["rate<0.00001"],
    dropped_iterations: ["count<1"],
  },
};

const targetBaseUrl = __ENV.TARGET_BASE_URL;

export default function () {
  const nickname = `s${String(exec.scenario.iterationInTest).padStart(9, "0")}`;
  const response = http.post(
    `${targetBaseUrl}/api/v1/users/sign-up`,
    JSON.stringify({
      nickname,
      thinkerScore: 10,
      foodieScore: 20,
      artistScore: 30,
      remembererScore: 40,
    }),
    { headers: { "Content-Type": "application/json" } },
  );

  let body = null;
  try {
    body = response.json();
  } catch (_) {
    // 응답 계약 검증에서 실패로 처리한다.
  }

  check(response, {
    "HTTP 200을 반환한다": (result) => result.status === 200,
    "성공 응답이다": () => body !== null && body.success === true,
    "요청한 닉네임을 반환한다": () => body !== null && body.data?.nickname === nickname,
    "생성된 사용자 식별 정보가 있다": () =>
      body !== null && body.data?.userId != null && body.data?.identificationCode != null,
  });
}
