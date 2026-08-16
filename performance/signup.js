import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";

const profile = __ENV.PROFILE;
const targetRps = Number(__ENV.RPS);
const testId = __ENV.TEST_ID;

function scaledRate(percent) {
  return Math.max(1, Math.ceil((targetRps * percent) / 100));
}

function heldStages(percentages, duration) {
  return percentages.flatMap((percent, index) => {
    const target = scaledRate(percent);
    const hold = { target, duration };
    return index === 0 ? [hold] : [{ target, duration: "0s" }, hold];
  });
}

function measuredScenario() {
  if (profile === "smoke") {
    return {
      executor: "constant-arrival-rate",
      rate: 1,
      timeUnit: "1s",
      duration: "30s",
      gracefulStop: "0s",
      preAllocatedVUs: 5,
      maxVUs: 100,
    };
  }
  if (profile === "load") {
    return {
      executor: "constant-arrival-rate",
      rate: targetRps,
      timeUnit: "1s",
      duration: "10m",
      gracefulStop: "0s",
      preAllocatedVUs: targetRps,
      maxVUs: 100,
    };
  }
  if (profile === "stress") {
    return {
      executor: "ramping-arrival-rate",
      startRate: scaledRate(20),
      timeUnit: "1s",
      gracefulStop: "0s",
      preAllocatedVUs: targetRps,
      maxVUs: 100,
      stages: heldStages([20, 40, 60, 80, 100], "3m"),
    };
  }
  if (profile === "spike") {
    return {
      executor: "ramping-arrival-rate",
      startRate: scaledRate(10),
      timeUnit: "1s",
      gracefulStop: "0s",
      preAllocatedVUs: targetRps,
      maxVUs: 100,
      stages: heldStages([10, 100, 10], "1m"),
    };
  }
  if (profile === "warmup") {
    return {
      executor: "constant-arrival-rate",
      rate: 1,
      timeUnit: "1s",
      duration: "30s",
      gracefulStop: "0s",
      preAllocatedVUs: 5,
      maxVUs: 100,
    };
  }
  throw new Error(`지원하지 않는 k6 프로필이다: ${profile}`);
}

const strictThresholds = profile === "smoke" || profile === "load";

export const options = {
  scenarios: {
    [profile]: measuredScenario(),
  },
  summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
  tags: testId ? { testid: testId } : {},
  thresholds: strictThresholds
    ? {
        checks: ["rate==1"],
        http_req_failed: ["rate==0"],
        dropped_iterations: ["count<1"],
      }
    : {},
};

const targetBaseUrl = __ENV.TARGET_BASE_URL;

export default function () {
  const nicknamePrefix = profile === "warmup" ? "w" : "m";
  const nickname = `${nicknamePrefix}${String(exec.scenario.iterationInTest).padStart(9, "0")}`;
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

function metricValues(data, name) {
  return data.metrics[name]?.values ?? {};
}

function normalizedSummary(data) {
  const requests = metricValues(data, "http_reqs");
  const failures = metricValues(data, "http_req_failed");
  const checks = metricValues(data, "checks");
  const dropped = metricValues(data, "dropped_iterations");
  const duration = metricValues(data, "http_req_duration");

  return {
    schemaVersion: 1,
    testid: testId,
    profile,
    rps: targetRps,
    measurementStartedAtUtc: __ENV.MEASUREMENT_STARTED_AT,
    measurementEndedAtUtc: new Date().toISOString(),
    metrics: {
      throughput: { count: requests.count ?? 0, rate: requests.rate ?? 0 },
      errorRate: failures.rate ?? 0,
      checksRate: checks.rate ?? 0,
      droppedIterations: dropped.count ?? 0,
      responseTimeMs: {
        p50: duration.med ?? 0,
        p95: duration["p(95)"] ?? 0,
        p99: duration["p(99)"] ?? 0,
      },
    },
    k6: data,
  };
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function htmlReport(summary) {
  const metrics = summary.metrics;
  const rows = [
    ["처리량 (req/s)", metrics.throughput.rate],
    ["요청 수", metrics.throughput.count],
    ["오류율", metrics.errorRate],
    ["checks 성공률", metrics.checksRate],
    ["dropped iterations", metrics.droppedIterations],
    ["p50 (ms)", metrics.responseTimeMs.p50],
    ["p95 (ms)", metrics.responseTimeMs.p95],
    ["p99 (ms)", metrics.responseTimeMs.p99],
  ]
    .map(([name, value]) => `<tr><th>${escapeHtml(name)}</th><td>${escapeHtml(value)}</td></tr>`)
    .join("");

  return `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>${escapeHtml(summary.testid)} 성능 결과</title>
<style>body{font:16px system-ui;max-width:900px;margin:40px auto;padding:0 20px;color:#172033}h1{font-size:1.6rem}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccd3df;padding:10px;text-align:left}th{background:#f3f6fa;width:45%}code{background:#eef2f7;padding:2px 5px}</style></head>
<body><h1>회원가입 성능 테스트 결과</h1>
<p><code>${escapeHtml(summary.testid)}</code> · ${escapeHtml(summary.profile)} · ${escapeHtml(summary.rps)} RPS</p>
<p>${escapeHtml(summary.measurementStartedAtUtc)} ~ ${escapeHtml(summary.measurementEndedAtUtc)}</p>
<table><tbody>${rows}</tbody></table></body></html>`;
}

function textReport(summary) {
  const metrics = summary.metrics;
  return [
    `testid=${summary.testid} profile=${summary.profile} rps=${summary.rps}`,
    `throughput=${metrics.throughput.rate}req/s errors=${metrics.errorRate} checks=${metrics.checksRate}`,
    `dropped=${metrics.droppedIterations} p50=${metrics.responseTimeMs.p50}ms p95=${metrics.responseTimeMs.p95}ms p99=${metrics.responseTimeMs.p99}ms`,
    "",
  ].join("\n");
}

export function handleSummary(data) {
  if (profile === "warmup" || !__ENV.RESULT_DIR) {
    return {};
  }
  const summary = normalizedSummary(data);
  return {
    stdout: textReport(summary),
    [`${__ENV.RESULT_DIR}/summary.json`]: JSON.stringify(summary, null, 2),
    [`${__ENV.RESULT_DIR}/report.html`]: htmlReport(summary),
  };
}
