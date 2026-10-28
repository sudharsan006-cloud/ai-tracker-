# AI Life Tracker — Phase 4 Build Prompt (step by step)
### Fitness: Steps, Water, Calories, Workouts, Exercise Library with How-To Images, Charts

You are a coding agent. Phases 1, 2 and 3 are built and working (auth, profile, bank accounts, expenses, SMS import, statement import, Flutter app for Android + Web). Now build Phase 4 exactly as written. Follow the steps IN ORDER. Do not skip a step. Do not add features that are not listed. After each step, run the check written under it. If a check fails, fix it before moving on. If you cannot fix it, stop and report the exact error.

---

## 0. RULES (read first)

1. Do not invent anything not in this file. If something is unclear, stop and ask.
2. Everything must cost $0. No paid APIs, no paid image hosting, no billing accounts.
3. Do not build anything from "OUT OF SCOPE" (section 9).
4. Do NOT change Phase 1, 2 or 3 behavior. Only add. Never edit existing migrations (`V1`, `V2`, `V3`). All new SQL goes into `V4__phase4_fitness.sql`.
5. **The backend calculates every number** (totals, percents, averages, calories burned, BMR). The Flutter app only displays numbers it receives. Never add, average or multiply values in Dart (a chart may plot the numbers it receives; that is not a calculation).
6. **No medical or diet advice.** The app only records, calculates and shows numbers. Never write text like "you should eat/drink/exercise X". Calculated values (BMR, calories burned) are always labelled "estimate".
7. Dates: every "day" means the day in the user's profile timezone (`user_profile.timezone`).
8. Never log tokens or passwords. No AI/Gemini in this phase.
9. No Lombok. Keep code simple and readable.
10. `kIsWeb` may only be used inside `PlatformCapabilities`. Step-sensor code must never be compiled into the web build (see B7).

---

## 1. WHAT PHASE 4 BUILDS

- **Goals:** daily goals for steps, water (ml) and calories (optional).
- **Steps:** counted from the phone sensor on Android; typed in manually on Web (and on Android if the user wants).
- **Water monitor:** quick-add buttons, a list of today's drinks, delete a wrong one.
- **Calories:** the user logs food entries (kcal + optional label). The backend sums them.
- **Workouts:** log a workout (name, date, duration, intensity, exercises with sets/reps/time/weight). The backend estimates calories burned.
- **Exercise library with how-to images:** about 250 home-friendly exercises, each with 2 photos showing how to do it and step-by-step instructions. Search, filters, favorites.
- **Charts:** last 7 days / 30 days bar charts and averages.
- **BMR estimate:** a resting-energy calculation from the user's profile (shown only if the profile has the needed data).

### Decisions already made (do not change)

- Exercise data comes from the open dataset **free-exercise-db** (https://github.com/yuhonas/free-exercise-db). Its license is **The Unlicense (public domain)**, so using it is free. We copy a filtered JSON file into our repo once. The images are NOT copied: the backend returns full image URLs that point to the dataset's public GitHub files, and the app loads them from there.
- Only home-friendly exercises are kept: equipment is one of `body only`, `dumbbell`, `bands`, `kettlebells`, `exercise ball`, `foam roll` (a `null` equipment becomes `body only`), and category is one of `strength`, `stretching`, `cardio`, `plyometrics`.
- Calories burned = `MET x weight(kg) x hours`, where MET comes from the intensity the user chose (light/moderate/vigorous, values in `system_config`). It needs the weight in the profile; without it the value is null.
- BMR uses the Mifflin-St Jeor formula, only for `sex_at_birth` = `male` or `female`.
- Step counting uses the phone's step sensor when the app is opened or when the user taps "Sync steps". There is NO background counting in Phase 4. Steps taken before the first sync of a day are not counted by the sensor (the user can type the number). This limitation is accepted.
- No reminders or notifications (Phase 6).

---

## 2. FILL-IN BY THE HUMAN

Nothing. (No samples are needed in this phase.)

---

## 3. FIRST: LOOK AT THE EXISTING CODE (step A0 / B0)

Before writing anything, list the existing backend packages and the Flutter `lib/` tree. Reuse and keep the same names and style: `ConfigService`, the error handler and JSON shape `{ "error", "message" }`, `ApiClient`, `PlatformCapabilities` (it already has `supportsNativeSteps`), the existing Fitness tab placeholder in `HomeShell`, `permission_handler` (added in Phase 2), `shared_preferences`.

---

# PART A — BACKEND

New Java packages: `com.lifetracker.fitness` and `com.lifetracker.exercise`.

## A1. Migration `V4__phase4_fitness.sql`

Create `src/main/resources/db/migration/V4__phase4_fitness.sql` with exactly this:

```sql
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
```

Do not create any other table. Check: the app starts, Flyway applies V4 on a dev database.

## A2. Configuration

Add to `application.yml` (merge into the existing file):

```yaml
app:
  exercise:
    image-base-url: ${EXERCISE_IMAGE_BASE_URL:https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/}
```

(If the images ever fail to load in the browser, the human can set the env var `EXERCISE_IMAGE_BASE_URL` to `https://cdn.jsdelivr.net/gh/yuhonas/free-exercise-db@main/exercises/` with no code change.)

## A3. The exercise data file (one-time download and filter)

1. Download `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json` into a temp folder OUTSIDE the project source (for example `tools/work/exercises_full.json`). If you cannot download it (no network), STOP and tell the human to download that file and put it in `tools/work/`.
2. Write a small script `tools/filter_exercises.py` (Python 3, standard library only; or Node if Python is not available) that:
   - keeps an exercise only if `equipment` (null treated as `"body only"`) is one of: `body only`, `dumbbell`, `bands`, `kettlebells`, `exercise ball`, `foam roll`, AND `category` is one of: `strength`, `stretching`, `cardio`, `plyometrics`, AND `images` has at least 1 item, AND `instructions` has at least 1 item;
   - writes ONLY these fields per exercise: `id`, `name`, `level`, `equipment` (with null replaced by `"body only"`), `category`, `force`, `mechanic`, `primaryMuscles`, `secondaryMuscles`, `instructions`, `images`;
   - writes the result to `backend/src/main/resources/seed/exercises.json` (a JSON array);
   - prints how many exercises were kept.
3. Run the script. Check: the printed count is at least 150. If it is lower, STOP and report the number and the distinct `equipment` and `category` values found.
4. Add a file `backend/src/main/resources/seed/README.txt` with this exact text: `Exercise data: free-exercise-db by yuhonas, The Unlicense (public domain). https://github.com/yuhonas/free-exercise-db`

## A4. Exercise seeder

`ExerciseSeeder implements ApplicationRunner` (runs on every boot):
1. Read `seed/exercises.json` from the classpath with Jackson.
2. `SELECT count(*) FROM exercises`. If the count is greater than or equal to the number of entries in the file → do nothing.
3. Otherwise insert every entry with `INSERT ... ON CONFLICT (external_id) DO NOTHING` using `JdbcTemplate.batchUpdate`. `external_id` = the JSON `id`. For the four array columns create SQL arrays with `connection.createArrayOf("text", stringArray)` (use `ps.getConnection()` inside the `PreparedStatementSetter`).
4. Never fail the application start because of a seeding problem: wrap in try/catch, log a warning (no data in the log) and continue.

Image URL builder (a small pure function, unit tested): `imageUrls(List<String> relativePaths)` returns `baseUrl + encodedPath` where each path is split on `/` and EACH segment is percent-encoded (use `URLEncoder.encode(segment, UTF_8).replace("+", "%20")`), then rejoined with `/`. The base URL always ends with `/` (add it if missing).

## A5. Exercise endpoints (auth required)

- `GET /api/v1/exercises` — query params (all optional): `q` (search text), `muscle`, `equipment`, `level`, `category`, `favoritesOnly` (true/false), `page` (default 0), `size` (default 30, max 100). Sorted by `name`. Rules:
  - `q`: `name ILIKE '%' || :q || '%'` (escape `%` and `_` in the input).
  - `muscle`: `:muscle = ANY(primary_muscles)`.
  - `equipment`, `level`, `category`: exact match.
  - `favoritesOnly=true`: only exercises in `exercise_favorites` for this user.
  - Response: `{ "items": [ExerciseSummary], "page", "size", "total" }`.
  - `ExerciseSummary`: `{ "id", "name", "level", "equipment", "category", "primaryMuscles", "thumbnailUrl", "isFavorite" }` (`thumbnailUrl` = first image URL or null).
- `GET /api/v1/exercises/{id}` → `{ "id", "name", "level", "equipment", "category", "force", "mechanic", "primaryMuscles", "secondaryMuscles", "instructions", "imageUrls", "isFavorite" }`. Unknown id → 404.
- `GET /api/v1/exercises/filters` → `{ "muscles": [...], "equipment": [...], "levels": [...], "categories": [...] }` (distinct values from the table, sorted; muscles via `SELECT DISTINCT unnest(primary_muscles)`; skip null levels).
- `POST /api/v1/exercises/{id}/favorite` → 204 (idempotent: `ON CONFLICT DO NOTHING`). `DELETE /api/v1/exercises/{id}/favorite` → 204.

Use `JdbcTemplate` (read arrays with `rs.getArray("col").getArray()` cast to `String[]`).

## A6. Goals

- `GET /api/v1/fitness/goals` → `{ "stepsGoal", "waterGoalMl", "calorieGoal" }`. If the user has no row: return the defaults from `ConfigService` (`default_steps_goal`, `default_water_goal_ml`) and `calorieGoal: null`. (Do not create a row on GET.)
- `PUT /api/v1/fitness/goals` body `{ "stepsGoal", "waterGoalMl", "calorieGoal" }` (calorieGoal may be null). Validate with the same ranges as the table, else 400. Upsert. Return the same shape.

## A7. Daily log endpoints (auth required, own data only)

**Date rule (used by every endpoint with a `date`):** `date` is `yyyy-MM-dd`. If absent → today in the user's timezone. It must not be after today and not before today minus 60 days, else 400.

### Steps
`PUT /api/v1/fitness/daily/steps` body `{ "date"?, "steps", "source" }`, `source` is `sensor` or `manual`, `steps` 0..200000. Rules (apply in order):
1. No row for that day → insert it with these values.
2. Existing row has `steps_source='manual'` and the new `source` is `sensor` → change nothing.
3. Existing row has `steps_source='sensor'` and the new `source` is `sensor` and the new `steps` is LOWER than the stored value → change nothing.
4. Otherwise → update `steps`, `steps_source`, `updated_at`.
Always return 200 with `{ "date", "steps", "stepsSource" }` showing the stored values after the rule.

### Water
- `POST /api/v1/fitness/water` body `{ "amountMl", "date"? }` → creates an entry (`log_date` from the date rule). Return `{ "id", "date", "amountMl", "loggedAt" }`.
- `GET /api/v1/fitness/water?date=` → entries of that day, newest first, same shape.
- `DELETE /api/v1/fitness/water/{id}` → 204 (other users' id → 404).

### Calories
- `POST /api/v1/fitness/calories` body `{ "kcal", "label"?, "date"? }` → `{ "id", "date", "kcal", "label", "loggedAt" }`.
- `GET /api/v1/fitness/calories?date=` → entries of that day, newest first.
- `DELETE /api/v1/fitness/calories/{id}` → 204 (other users' id → 404).

### Summary and range
Implement ONE range query used by both endpoints (run it with `JdbcTemplate`/`NamedParameterJdbcTemplate`; use `CAST(:from AS date)` and not `::date` on parameters):

```sql
SELECT d::date AS day,
  COALESCE(f.steps, 0) AS steps,
  COALESCE(f.steps_source, 'none') AS steps_source,
  COALESCE((SELECT SUM(w.amount_ml) FROM water_entries w
             WHERE w.user_id = :userId AND w.log_date = d::date), 0) AS water_ml,
  COALESCE((SELECT SUM(c.kcal) FROM calorie_entries c
             WHERE c.user_id = :userId AND c.log_date = d::date), 0) AS calories_in,
  COALESCE((SELECT SUM(k.calories_burned_est) FROM workouts k
             WHERE k.user_id = :userId AND k.workout_date = d::date), 0) AS calories_burned_est,
  (SELECT COUNT(*) FROM workouts k
    WHERE k.user_id = :userId AND k.workout_date = d::date) AS workout_count
FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
LEFT JOIN fitness_daily_log f ON f.user_id = :userId AND f.log_date = d::date
ORDER BY day;
```

**GET /api/v1/fitness/daily?date=** → run the query with `from = to = date`, then add goals and percents:
```json
{ "date", "steps", "stepsSource", "stepsGoal", "stepsPercent",
  "waterMl", "waterGoalMl", "waterPercent",
  "caloriesIn", "calorieGoal", "caloriesPercent",
  "caloriesBurnedEst", "workoutCount", "bmrEstimate" }
```
- `xPercent` = `floor(value * 100 / goal)` as an integer (can be above 100). `caloriesPercent` is null when `calorieGoal` is null.
- `bmrEstimate` = BMR (A8) or null.

**GET /api/v1/fitness/range?from=&to=** → both dates in `yyyy-MM-dd`, `to` not before `from`, at most 62 days apart, `to` not after today, else 400. Response:
```json
{ "days": [ { "date", "steps", "waterMl", "caloriesIn", "caloriesBurnedEst", "workoutCount" } ],
  "averages": { "steps", "waterMl", "caloriesIn" } }
```
`averages` = sum divided by the number of days in the range (days with zero count too), rounded HALF_UP to a whole number, calculated in Java with `BigDecimal`.

## A8. BMR (pure function, unit tested)

`bmrEstimate(sexAtBirth, weightKg, heightCm, dateOfBirth, onDate)` → `Integer` or `null`.
- Return `null` if any input is null, or `sexAtBirth` is not `male`/`female`.
- `age` = whole years between `dateOfBirth` and `onDate`.
- `base = 10 * weightKg + 6.25 * heightCm - 5 * age` (use `BigDecimal`).
- male: `base + 5`; female: `base - 161`.
- Round HALF_UP to an integer.

## A9. Workouts

**Intensity → MET:** `light` → `workout_met_light`, `moderate` → `workout_met_moderate`, `vigorous` → `workout_met_vigorous` (from `ConfigService`).

**Calories burned estimate** (pure function, unit tested): `round_half_up(MET * weightKg * durationMin / 60)`. Returns `null` if the profile has no `weight_kg`. Weight is read from the user's profile at save time.

**Endpoints (auth required, own data only; others' ids → 404):**

- `POST /api/v1/workouts` body:
```json
{ "date": "yyyy-MM-dd or omitted", "name": "Morning routine", "durationMin": 30,
  "intensity": "moderate", "notes": "optional",
  "entries": [ { "exerciseId": "uuid", "sets": 3, "reps": 12, "durationSeconds": null, "weightKg": null },
               { "customName": "Skipping", "sets": 1, "reps": null, "durationSeconds": 300, "weightKg": null } ] }
```
  Validation (400 on failure): `name` 1–80 chars; `durationMin` 1–600; `intensity` one of the three; date rule from A7; `entries` has 1 to 40 items; for each entry EXACTLY ONE of `exerciseId` / `customName` (customName 1–80 chars); `exerciseId` must exist; `sets` 1–50; at least one of `reps` (1–500) or `durationSeconds` (1–7200); `weightKg` 0–1000 or null. Save the workout and the entries (`position` = index in the list) in one transaction, with `calories_burned_est` computed.
- `GET /api/v1/workouts?from=&to=&page=&size=` → paged (`size` default 20, max 100), newest first: `{ "items": [Workout], "page", "size", "total" }`.
- `GET /api/v1/workouts/{id}` → `Workout`.
- `PUT /api/v1/workouts/{id}` → same body as POST; replaces the workout fields and ALL entries (delete the old entries, insert the new ones, in one transaction) and recomputes the estimate.
- `DELETE /api/v1/workouts/{id}` → 204.

**Workout JSON:**
```json
{ "id", "date", "name", "durationMin", "intensity", "caloriesBurnedEst", "notes",
  "entries": [ { "id", "position", "exerciseId", "exerciseName", "thumbnailUrl",
                 "sets", "reps", "durationSeconds", "weightKg" } ] }
```
`exerciseName` = library name or `customName`; `thumbnailUrl` = first image URL of the library exercise, otherwise null.

## A10. Backend tests (Testcontainers Postgres, as before)

All must pass:

1. `bmrEstimate`: male, 30 years, 80 kg, 180 cm → 1780; female, same → 1614; sex `other` or any null input → null; the age uses the given `onDate`.
2. `caloriesBurnedEstimate`: MET 5, 70 kg, 60 min → 350; MET 3, 80 kg, 30 min → 120; no weight → null.
3. Image URL builder: adds a missing trailing `/`; encodes spaces and special characters per segment; keeps the `/` between segments.
4. The seed file: valid JSON array, at least 150 entries, every entry has a unique `id`, at least 1 image and 1 instruction, and `equipment` is in the allowed list.
5. The seeder: after startup the `exercises` table count equals the number of entries in the file; running the seeder again does not change the count.
6. Exercise list: `q` matches case-insensitively; `muscle` filter; `equipment` filter; `favoritesOnly`; `page`/`size`; `total` is correct. Favorite then unfavorite; favoriting twice is fine. Detail of an unknown id → 404.
7. Steps rules: first write inserts; `manual` then `sensor` → stored value unchanged; `sensor` 5000 then `sensor` 4000 → stays 5000; `sensor` 5000 then `sensor` 6000 → 6000; `sensor` then `manual` → manual wins and stores.
8. Date rule: tomorrow → 400; 61 days ago → 400; omitted → today in the user's timezone (use a user with timezone `Pacific/Kiritimati` or similar far from UTC and check the date is computed in that zone).
9. Water and calories: create, list newest first, delete; deleting another user's entry → 404; amount 0 or 5001 → 400.
10. Daily summary: goals default when no row; percent = floor(value*100/goal); a day with no data returns zeros; `bmrEstimate` null when the profile has no weight; after the user sets goals the summary uses them.
11. Range: correct number of days including empty days; averages are correct (e.g. steps 3000 on one day of a 3-day range, others 0 → average 1000); more than 62 days → 400.
12. Workout create: with a library exercise and with a custom exercise; both `exerciseId` and `customName` → 400; neither → 400; neither reps nor duration → 400; 0 entries → 400; `caloriesBurnedEst` is correct and null when the profile has no weight.
13. Workout PUT replaces all entries and recomputes the estimate; DELETE removes the workout and its entries; the daily summary's `caloriesBurnedEst` and `workoutCount` include the workout.
14. Isolation: another user can never read, change or delete the first user's goals, entries, workouts (404).
15. Regression: a user with fitness data, workouts, favorites AND earlier-phase data can call `DELETE /api/v1/account` successfully.

Check: `mvn test` passes. If Docker is not available for Testcontainers, still write the tests and say so in your report.

---

# PART B — FLUTTER APP

## B0. Look at the existing Flutter code first (see section 3).

## B1. Packages
Add: `fl_chart` (charts), `pedometer` (step sensor, Android). `intl`, `permission_handler`, `shared_preferences` already exist. If `pedometer` fails to resolve or build, stop and report; do not silently swap packages.

Android: add `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION"/>` to `AndroidManifest.xml`.

## B2. Folder structure (add)

```
lib/
  fitness/
    models.dart, fitness_repository.dart,
    fitness_tab.dart, goals_screen.dart, charts_screen.dart,
    workout_form_screen.dart, workouts_screen.dart
  exercises/
    exercise_library_screen.dart, exercise_detail_screen.dart
  steps/
    steps_reader.dart, steps_reader_stub.dart, steps_reader_io.dart, steps_sync_service.dart
```

## B3. Models and repository
`models.dart`: plain Dart classes with `fromJson` for `FitnessGoals`, `DailySummary`, `WaterEntry`, `CalorieEntry`, `ExerciseSummary`, `ExerciseDetail`, `ExerciseFilters`, `Workout`, `WorkoutEntry`, `RangeResponse`. `fitness_repository.dart`: one class wrapping `ApiClient` with a method for every endpoint in Part A. Do not calculate anything in Dart.

## B4. Fitness tab (replaces the "Coming soon" body of the Fitness tab only)

A scrollable dashboard for ONE selected day:
- **Date bar:** previous-day / next-day arrows and the date text. The next-day arrow is disabled on today. Changing the day reloads everything.
- **Steps card:** a circular progress ring (`CircularProgressIndicator`, value = `min(stepsPercent / 100, 1.0)`), big number `steps`, text "of {stepsGoal}" and the source ("from phone sensor" / "entered manually" / "no data yet"). Buttons: "Edit" (dialog to type steps → `PUT steps` with source `manual`) and, ONLY when `PlatformCapabilities.supportsNativeSteps`, "Sync steps" (B7).
- **Water card:** ring + `waterMl` of `waterGoalMl`. Quick-add buttons `+150`, `+250`, `+500` ml and a "Custom" button (dialog). Below: today's drink list (time + ml) with a delete icon on each (confirm not needed). After each action, reload the summary and the list.
- **Calories card:** `caloriesIn` and, if a goal exists, "of {calorieGoal}" with a ring. Button "Add food" (dialog: kcal number, optional label) and the day's entries list with delete icons. A smaller line: "Burned in workouts (estimate): {caloriesBurnedEst}". If `bmrEstimate` is not null: "Resting energy estimate (BMR): {bmrEstimate} kcal. A calculation from your profile." If null: "Add sex, birth date, height and weight in your profile to see a resting energy estimate." No advice text anywhere.
- **Workouts card:** the day's workouts (name, duration, estimated calories) from `GET /workouts?from=date&to=date`; tap opens the workout; button "Log workout" opens the Workout form.
- **Buttons row:** "Exercises" (library), "Workout history", "Charts", "Goals".
- Pull-to-refresh reloads the tab.

## B5. Exercise library and how-to screens

**Exercise library** (`exercise_library_screen.dart`), constructor parameter `pickMode` (bool, default false):
- Search box (debounce 400 ms) and a filter row: dropdowns for muscle, equipment, level, category (options from `GET /exercises/filters`, each with an "All" option) and a "Favorites" toggle.
- Paginated list (load the next page when scrolling near the end). Each row: a square thumbnail (`Image.network(thumbnailUrl, width: 64, height: 64, fit: BoxFit.cover, cacheWidth: 200)` with a `loadingBuilder` spinner and an `errorBuilder` showing `Icons.fitness_center`), the name, a line "{primary muscles} · {equipment} · {level}", and a star icon that toggles favorite.
- Tap a row → Exercise detail. In `pickMode`, the detail screen shows an "Add to workout" button that closes both screens and returns the chosen exercise to the caller.

**Exercise detail** (`exercise_detail_screen.dart`), parameters `exerciseId` and `pickMode`:
- **How-to pictures:** the exercise has 2 photos (start and end position). Show them large (4:3 area, `BoxFit.contain`) using an `AnimatedSwitcher` (fade, 300 ms) that alternates between image 1 and image 2 every 1 second (a `Timer.periodic`, cancelled in `dispose`). Also show two small thumbnails under the picture; tapping one pauses the animation and shows that image; tapping the picture resumes the animation. If there is only one image, just show it. Every image uses `loadingBuilder` and an `errorBuilder` with `Icons.image_not_supported`.
- Name as the title; chips for level, equipment, category, primary muscles; a separate line for secondary muscles.
- **Instructions** as a numbered list (1., 2., 3., ...), one item per instruction string.
- A favorite star in the app bar.
- Button "Add to workout": in `pickMode` it returns the exercise to the caller; otherwise it opens the Workout form with this exercise already added as its first entry.
- Small footer text: "Exercise data: free-exercise-db (public domain)".

## B6. Workout screens

**Workout form** (`workout_form_screen.dart`), parameters: optional `existingWorkout` (edit mode) and optional `initialExercise` (pre-added entry):
- Fields: name (text), date (picker, default the selected day, not in the future), duration in minutes (number), intensity (dropdown: Light / Moderate / Vigorous), notes (optional).
- Entries list. Buttons "Add from library" (opens the library with `pickMode: true`) and "Add custom exercise" (dialog: name). Each entry card shows the exercise name (and its thumbnail if it has one), and number fields: sets, reps, duration (seconds), weight (kg), plus a delete icon and move up / move down arrows. Each entry needs sets and at least one of reps or duration.
- Save → `POST` (or `PUT` in edit mode). Show API error messages under the form. After saving, go back and reload.
- After a successful save show a SnackBar: "Saved. Calories burned (estimate): {caloriesBurnedEst}" or, when null, "Saved. Add your weight in the profile to see a calorie estimate."

**Workout history** (`workouts_screen.dart`): paginated list (newest first): date, name, duration, estimated calories. Tap → Workout form in edit mode (with a Delete action in the app bar after a confirm dialog).

## B7. Steps sensor (Android only) — web must compile without it

Use the same conditional-export pattern as the SMS code in Phase 2.

`lib/steps/steps_reader.dart` (this one line):
```dart
export 'steps_reader_stub.dart' if (dart.library.io) 'steps_reader_io.dart';
```

Both `steps_reader_stub.dart` and `steps_reader_io.dart` define the same two top-level functions:
```dart
Future<bool> ensureActivityPermission();
Future<int?> readStepsSinceBoot();
```
- `steps_reader_stub.dart`: returns `false` and `null`. No plugin imports.
- `steps_reader_io.dart`: `ensureActivityPermission` uses `permission_handler` (`Permission.activityRecognition.request()`); `readStepsSinceBoot` returns the first value of `Pedometer.stepCountStream` (`.steps`) with a 5-second timeout, or `null` on any error or timeout.

`steps_sync_service.dart`, method `Future<int?> sync()` (returns the day's steps or null):
1. If `!PlatformCapabilities.supportsNativeSteps` → return null.
2. If `!await ensureActivityPermission()` → return null (the UI shows "Activity permission is needed to count steps").
3. `total = await readStepsSinceBoot()`; if null → return null.
4. Read these `shared_preferences` keys: `steps_day` (String `yyyy-MM-dd`, the device's local date), `steps_last_total` (int), `steps_accum` (int). Let `today` be the device's local date.
5. If `steps_day != today` → set `steps_day = today`, `steps_accum = 0`, `steps_last_total = total`.
6. Else: `delta = total - steps_last_total`; if `delta < 0` (the phone was rebooted) then `delta = total`; `steps_accum += delta`; `steps_last_total = total`.
7. Save the three keys. Call `PUT /fitness/daily/steps` with `{ steps: steps_accum, source: "sensor" }` (no date field). Return the `steps` from the response.

The sync runs when the Fitness tab opens (Android only, today only) and when the user taps "Sync steps". After it runs, reload the summary.

## B8. Goals and Charts

- **Goals screen:** three number fields (steps goal, water goal in ml, calorie goal optional). Save → `PUT goals`. Show API validation errors.
- **Charts screen:** a toggle "7 days / 30 days". Loads `GET /fitness/range` for the last 7 or 30 days ending today. Shows three bar charts with `fl_chart` (`BarChart`): steps per day, water (ml) per day, calories in per day. Under each chart one line "Average: {averages.x}" taken from the response. Bottom axis shows the day-of-month number. Plot only the numbers received. If there is no data at all, show "No data yet".

## B9. Flutter checks
- `flutter analyze` has no errors.
- `flutter build web` succeeds (this proves the pedometer code did not leak into web).
- On Android: sync steps asks for the permission and updates the card; water, calories, goals, workouts, library, charts all work.
- On web: everything works; there is no "Sync steps" button; exercise pictures load in the browser (if they do not load on web, set `EXERCISE_IMAGE_BASE_URL` to the jsDelivr URL from A2 and redeploy the backend, then check again).
- The how-to picture alternates between its two photos on the detail screen.

---

# PART C — DEPLOY

Push the code. Render rebuilds; Flyway applies `V4`; the seeder fills the `exercises` table on first boot. Redeploy the web build and rebuild the APK as before. No new services, no new cost.

---

# 8. DEFINITION OF DONE

- [ ] `V4__phase4_fitness.sql` applied; V1, V2, V3 untouched
- [ ] `seed/exercises.json` exists with at least 150 home-friendly exercises and the license note; the exercises table is filled by the seeder
- [ ] Exercise library: search, filters, favorites, pagination, detail screen with alternating how-to pictures, numbered instructions
- [ ] Water monitor: quick add, custom add, list, delete; goal ring
- [ ] Calories: add/list/delete food entries; goal ring; burned estimate and BMR shown only as labelled estimates
- [ ] Steps: sensor sync on Android with the documented rules, manual entry on Android and Web
- [ ] Workouts: create / edit / delete with library or custom exercises; backend-calculated calorie estimate
- [ ] Goals with defaults; 7/30-day charts with backend-calculated averages
- [ ] Backend returns every number; Flutter performs no calculations
- [ ] `mvn test` passes, `flutter analyze` is clean, `flutter build web` succeeds
- [ ] Deployed on the existing Render + Neon + Firebase Hosting setup. Total cost: $0

# 9. OUT OF SCOPE (do not build)

Reminders and push notifications (Phase 6). Anything Gemini/AI and the mood log (Phase 5). Background step counting, Health Connect, Google Fit, wearables, GPS or route tracking. Body-weight history, food database or barcode scanning, meal plans, diet or workout recommendations, streaks and badges, social features. Uploading your own exercise photos or videos. Copying the exercise images into our own storage. Workout templates or routines. Any medical or diet advice text.

When every box in section 8 is checked, Phase 4 is done and Phase 5 (AI insights with Gemini free tier) can start.
