CREATE TABLE fitness_goals (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    steps_goal INT NOT NULL CHECK (steps_goal BETWEEN 100 AND 100000),
    water_goal_ml INT NOT NULL CHECK (water_goal_ml BETWEEN 250 AND 10000),
    calorie_goal INT CHECK (calorie_goal IS NULL OR calorie_goal BETWEEN 500 AND 10000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fitness_daily_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    steps INT NOT NULL DEFAULT 0 CHECK (steps BETWEEN 0 AND 200000),
    steps_source VARCHAR(10) NOT NULL DEFAULT 'none' CHECK (steps_source IN ('none','sensor','manual')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, log_date)
);

CREATE TABLE water_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    amount_ml INT NOT NULL CHECK (amount_ml BETWEEN 1 AND 5000),
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_water_user_date ON water_entries (user_id, log_date);

CREATE TABLE calorie_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    kcal INT NOT NULL CHECK (kcal BETWEEN 1 AND 10000),
    label VARCHAR(80),
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_calorie_user_date ON calorie_entries (user_id, log_date);

CREATE TABLE exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(120) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    level VARCHAR(20),
    equipment VARCHAR(30) NOT NULL,
    category VARCHAR(30) NOT NULL,
    force VARCHAR(20),
    mechanic VARCHAR(20),
    primary_muscles TEXT[] NOT NULL DEFAULT '{}',
    secondary_muscles TEXT[] NOT NULL DEFAULT '{}',
    instructions TEXT[] NOT NULL DEFAULT '{}',
    images TEXT[] NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_exercises_name_trgm ON exercises USING gin (name gin_trgm_ops);

CREATE TABLE exercise_favorites (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, exercise_id)
);

CREATE TABLE workouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_date DATE NOT NULL,
    name VARCHAR(80) NOT NULL,
    duration_min INT NOT NULL CHECK (duration_min BETWEEN 1 AND 600),
    intensity VARCHAR(10) NOT NULL CHECK (intensity IN ('light','moderate','vigorous')),
    calories_burned_est INT,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workouts_user_date ON workouts (user_id, workout_date DESC);

CREATE TABLE workout_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_id UUID NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    position INT NOT NULL,
    exercise_id UUID REFERENCES exercises(id),
    custom_name VARCHAR(80),
    sets INT NOT NULL CHECK (sets BETWEEN 1 AND 50),
    reps INT CHECK (reps IS NULL OR reps BETWEEN 1 AND 500),
    duration_seconds INT CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 1 AND 7200),
    weight_kg NUMERIC(5,1) CHECK (weight_kg IS NULL OR weight_kg BETWEEN 0 AND 1000),
    CHECK ((exercise_id IS NOT NULL AND custom_name IS NULL) OR (exercise_id IS NULL AND custom_name IS NOT NULL)),
    CHECK (reps IS NOT NULL OR duration_seconds IS NOT NULL)
);

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('default_steps_goal', 8000, 100, 100000, 'Daily steps goal used until the user sets their own'),
    ('default_water_goal_ml', 2000, 250, 10000, 'Daily water goal (ml) used until the user sets their own'),
    ('workout_met_light', 3.0, 1.0, 20.0, 'MET value for light intensity workouts'),
    ('workout_met_moderate', 5.0, 1.0, 20.0, 'MET value for moderate intensity workouts'),
    ('workout_met_vigorous', 8.0, 1.0, 20.0, 'MET value for vigorous intensity workouts');
