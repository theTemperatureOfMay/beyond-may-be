-- 최소 인증 토큰 체계.
-- Redis 없이(ADR-0006) DB 기반 opaque token으로 로그인 세션을 식별한다.

CREATE TABLE public.auth_tokens (
    token character varying(64) PRIMARY KEY,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    user_id bigint NOT NULL
);

CREATE INDEX idx_auth_tokens_user_id ON public.auth_tokens (user_id);
