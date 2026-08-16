-- 방문 인증 반경(100m) 검증에는 위도·경도 소수점 이하 최소 6자리 정밀도가
-- 필요하다 (architecture/backend.md). numeric(38,2)는 이를 충족하지 못해
-- numeric(9,6)으로 좁힌다. 기존 값은 그대로 유지된다(additive 범위 변경).

ALTER TABLE public.places
    ALTER COLUMN latitude TYPE numeric(9,6),
    ALTER COLUMN longitude TYPE numeric(9,6);
