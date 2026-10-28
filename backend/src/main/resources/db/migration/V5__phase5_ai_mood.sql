CREATE TABLE mood_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 5),
    note VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, log_date)
);

CREATE TABLE user_ai_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    ai_enabled BOOLEAN NOT NULL DEFAULT true,
    include_mood BOOLEAN NOT NULL DEFAULT false,
    mood_nudge_dismissed_on DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('spending_month','fitness_week','overview_week')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    facts_hash VARCHAR(64) NOT NULL,
    prompt_version INT NOT NULL,
    model VARCHAR(60) NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, kind, period_start, period_end, facts_hash, prompt_version, model)
);
CREATE INDEX idx_ai_insights_user_kind ON ai_insights (user_id, kind, created_at DESC);

CREATE TABLE ai_call_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    called_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind VARCHAR(20) NOT NULL,
    http_status INT,
    success BOOLEAN NOT NULL
);
CREATE INDEX idx_ai_call_log_user_time ON ai_call_log (user_id, called_at DESC);

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('ai_insight_daily_call_budget', 40, 1, 1000, 'Max Gemini calls per user per day (our own cap, kept below the free tier quota)'),
    ('ai_insight_min_interval_seconds', 6, 0, 120, 'Minimum seconds between two Gemini calls'),
    ('mood_nudge_window_days', 7, 3, 30, 'How many recent days the mood nudge looks at'),
    ('mood_nudge_low_score_max', 2, 1, 4, 'A mood score at or below this counts as a low day'),
    ('mood_nudge_min_low_days', 4, 1, 30, 'How many low days inside the window trigger the nudge'),
    ('mood_nudge_cooldown_days', 7, 1, 30, 'After the user dismisses the nudge, hide it for this many days');
