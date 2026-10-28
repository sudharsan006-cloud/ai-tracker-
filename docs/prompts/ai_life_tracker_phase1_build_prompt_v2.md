# AI Life Tracker — Phase 1 Build Prompt (v2, step by step)
### Foundation: Backend Auth + Reference Data + Flutter App (Android + Web) + Video Onboarding

You are a coding agent. Build exactly what is written here. Follow the steps IN ORDER. Do not skip a step. Do not add features that are not listed. After each step, run the check written under it. If a check fails, fix it before moving on. If you cannot fix it, stop and report the exact error.

---

## 0. RULES (read first)

1. Do not invent anything not in this file. If something is unclear, stop and ask.
2. Everything must cost $0. Do not add paid services. Do not create billing accounts.
3. Do not build anything from "OUT OF SCOPE" (section 9).
4. Keep code simple and readable. No clever tricks. No Lombok. No code generation tools except where stated.
5. Never log passwords, tokens, or the JWT secret.
6. Never commit secrets. Use environment variables. Add `.env` and `*.env` to `.gitignore`.
7. Backend calculates every number. (Not relevant yet in Phase 1, but do not break this rule later.)

---

## 1. WHAT WE ARE BUILDING

A personal, single-user "AI Life Tracker" app (expenses + home fitness, later with AI summaries). Phase 1 builds only:

- Backend: signup, login, token refresh (with reuse detection), logout, banks list, currencies list, bank accounts CRUD, profile, delete account.
- Flutter app: one codebase, two targets: Android (APK) and Web.
- A first-launch video onboarding (5 slides). On Web the slides scrub a video as the user swipes. On Android the same 5 slides show without video.

### Fixed tech choices (do not change)

| Part | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.5.x (NOT 4.x), Maven |
| DB | PostgreSQL on Neon (free), Flyway migrations |
| Auth | JWT access token + rotating refresh token, BCrypt passwords |
| Frontend | Flutter (latest stable), packages: `http`, `provider`, `flutter_secure_storage`, `shared_preferences`, `video_player` |
| Backend host | Render free web service (Docker) |
| Web host | Firebase Hosting (free Spark plan) |

### Fill-in by the human (the agent must not guess these)

```
BANKS_TO_SEED:
  short_code=SAMPLE ; name=Sample Bank        <- human replaces with their real bank(s) before first deploy
```

---

## 2. REPO LAYOUT

Create this folder structure:

```
ai-life-tracker/
  backend/            (Spring Boot project)
  app/                (Flutter project)
  .gitignore
```

Backend Java package root: `com.lifetracker`. Sub-packages: `auth`, `user`, `bank`, `config`, `common`.

---

# PART A — BACKEND

## A1. Create the project

Create a Spring Boot 3.5.x Maven project in `backend/`, Java 21, with these dependencies: Spring Web, Spring Security, Spring Data JPA, Validation, PostgreSQL Driver, Flyway (`flyway-core` AND `flyway-database-postgresql`), Spring Boot Actuator. Add JJWT 0.12.x (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Add test deps: spring-boot-starter-test, spring-security-test, Testcontainers (`postgresql`, `junit-jupiter`).

Check: `mvn -q compile` succeeds.

## A2. Configuration file

`src/main/resources/application.yml`:

```yaml
server:
  port: ${PORT:8080}
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 5
      max-lifetime: 300000
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
app:
  jwt:
    secret: ${JWT_SECRET}
    access-ttl-minutes: ${JWT_ACCESS_TTL_MINUTES:15}
    refresh-ttl-days: ${JWT_REFRESH_TTL_DAYS:30}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5000}
management:
  endpoints:
    web:
      exposure:
        include: health
```

Environment variables (all required except the ones with defaults):

```
DB_URL=jdbc:postgresql://<neon-host>/<dbname>?sslmode=require
DB_USER=<neon user>
DB_PASSWORD=<neon password>
JWT_SECRET=<random string, at least 32 characters>
CORS_ALLOWED_ORIGINS=<comma separated list of web origins>
```

IMPORTANT: `DB_URL` must be a JDBC URL (starts with `jdbc:postgresql://`). Do NOT paste Neon's `postgresql://user:pass@...` string as is. Use the Neon NON-pooled host (hostname without `-pooler`) so Flyway migrations work.

## A3. Database migration

Create `src/main/resources/db/migration/V1__phase1_foundation.sql` with exactly this:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    date_of_birth DATE,
    sex_at_birth VARCHAR(10),
    height_cm NUMERIC(5,1),
    weight_kg NUMERIC(5,1),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens(id)
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE banks (
    id SERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    short_code VARCHAR(20) UNIQUE NOT NULL,
    country_code VARCHAR(2) NOT NULL DEFAULT 'IN',
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE currencies (
    id SERIAL PRIMARY KEY,
    code VARCHAR(3) UNIQUE NOT NULL,
    name VARCHAR(60) NOT NULL,
    symbol VARCHAR(5) NOT NULL,
    decimal_places SMALLINT NOT NULL DEFAULT 2
);

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_id INT NOT NULL REFERENCES banks(id) ON DELETE RESTRICT,
    currency_id INT NOT NULL REFERENCES currencies(id) ON DELETE RESTRICT,
    account_last4 VARCHAR(4),
    nickname VARCHAR(60),
    account_type VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE system_config (
    key VARCHAR(80) PRIMARY KEY,
    value NUMERIC NOT NULL,
    min_value NUMERIC NOT NULL,
    max_value NUMERIC NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO currencies (code, name, symbol, decimal_places) VALUES ('INR', 'Indian Rupee', '₹', 2);

INSERT INTO system_config (key, value, min_value, max_value, description) VALUES
    ('bank_account_max_per_user', 20, 1, 100, 'Soft cap on bank accounts a single user can register');

-- Replace with the real banks from BANKS_TO_SEED (section 1):
INSERT INTO banks (name, short_code, country_code) VALUES ('Sample Bank', 'SAMPLE', 'IN');
```

Use the banks from `BANKS_TO_SEED` for the last INSERT. Do not seed any other banks. Do not create any other tables.

## A4. Common pieces

1. Error response JSON for every error: `{"error": "<CODE>", "message": "<human text>"}`. Codes: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `NOT_FOUND` (404), `CONFLICT` (409), `LIMIT_REACHED` (409).
2. A `@RestControllerAdvice` that maps: validation exceptions → 400, a custom `NotFoundException` → 404, `ConflictException` → 409, `UnauthorizedException` → 401. Unknown exceptions → 500 with a generic message (never leak stack traces).
3. All endpoints live under `/api/v1`.

## A5. Security setup

Create `SecurityConfig`:

- Stateless sessions, CSRF disabled (we use bearer tokens).
- Public (no token): `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /actuator/health`, and `OPTIONS` requests.
- Everything else requires a valid access token.
- IMPORTANT: configure `authenticationEntryPoint` to return HTTP **401** with the JSON error body. (Spring's default can return 403; the Flutter app relies on 401 to trigger refresh.)
- A `JwtAuthFilter` (extends `OncePerRequestFilter`): reads `Authorization: Bearer <token>`, validates it, sets the authenticated principal to the user's UUID. If the token is missing or invalid, just continue the chain (the entry point returns 401).
- CORS: allow origins from `app.cors.allowed-origins` (comma separated), methods `GET POST PUT PATCH DELETE OPTIONS`, headers `Authorization, Content-Type`, no credentials.
- Password encoder: `BCryptPasswordEncoder`.

`JwtService`: HS256, subject = user UUID string, expiry = `access-ttl-minutes`. Methods: `createAccessToken(UUID)` and `parseUserId(String token)` (throws on invalid/expired).

Check: app starts against a Neon database, Flyway runs V1, `GET /actuator/health` returns `{"status":"UP"}`.

## A6. Auth service and endpoints

Refresh tokens are random opaque strings: 32 bytes from `SecureRandom`, encoded base64url (no padding). In the DB store only the SHA-256 hex hash of the token (never the token).

Every auth response has this exact shape:

```json
{ "accessToken": "...", "refreshToken": "...", "expiresInSeconds": 900 }
```

### POST /api/v1/auth/signup
Body `{ "email", "password" }`. Rules: email must be a valid email (lowercase and trim it before saving); password at least 8 characters. If the email exists → 409 `CONFLICT`. Create the `users` row (BCrypt hash) AND an empty `user_profile` row (defaults only) in one transaction. Create a new refresh token with a NEW `family_id`. Return the auth response.

### POST /api/v1/auth/login
Body `{ "email", "password" }`. Wrong email OR wrong password OR inactive user → 401 with the same message `Invalid credentials` (do not reveal which). On success: new refresh token, new `family_id`, return the auth response.

### POST /api/v1/auth/refresh  (the most important endpoint — follow exactly)
Body `{ "refreshToken" }`.

```
hash = sha256Hex(refreshToken)
row  = refreshTokenRepo.findByTokenHash(hash)

1. row not found            -> return 401
2. row.revokedAt != null    -> REUSE DETECTED:
                                 revoke every token in row.familyId (set revokedAt = now where null)
                                 commit that change
                                 return 401
3. row.expiresAt < now      -> return 401
4. otherwise (valid):
     mark row.revokedAt = now
     create newToken in the SAME familyId
     row.replacedBy = newToken.id
     issue new access token
     return the auth response
```

CRITICAL BUG TO AVOID: in step 2 you must save the revocations and still answer 401. If you do the revoke inside a `@Transactional` method and then THROW an exception, the transaction rolls back and the revocation is lost. Instead: have the service method return a result object (`OK(tokens)` or `REJECTED`) and let the controller turn `REJECTED` into the 401. Do not throw inside the transactional method after revoking.

### POST /api/v1/auth/logout
Body `{ "refreshToken" }`. Find the token by hash; if found, revoke the whole family. Always return 204 (even if not found).

Check (write these as tests in section A9): reuse of an already-used refresh token revokes the family.

## A7. Reference + bank account + profile + config

### Banks and currencies (auth required)
- `GET /api/v1/banks` → list of active banks: `[{ "id", "name", "shortCode", "countryCode" }]`
- `GET /api/v1/currencies` → `[{ "id", "code", "name", "symbol", "decimalPlaces" }]`

### ConfigService (internal only, no endpoint)
Use `JdbcTemplate` on table `system_config`. `get(key)` returns BigDecimal (throw if key missing). `set(key, value)` must reject (IllegalArgumentException) a value outside `[min_value, max_value]`, otherwise update `value` and `updated_at`.

### Bank accounts (auth required, only the logged-in user's rows)
- `GET /api/v1/bank-accounts` → active accounts of the user.
- `POST /api/v1/bank-accounts` body `{ "bankId", "currencyId", "accountLast4"?, "nickname"?, "accountType"? }`
  - `bankId` must exist and be active, `currencyId` must exist → otherwise 400.
  - `accountLast4`, if present, must match `^[0-9]{4}$`.
  - `accountType`, if present, must be one of `savings`, `current`, `credit`.
  - Count the user's ACTIVE accounts; if count >= `ConfigService.get("bank_account_max_per_user")` → 409 `LIMIT_REACHED`.
- `PATCH /api/v1/bank-accounts/{id}` body may contain any of `nickname`, `accountLast4`, `accountType` (same validation). Another user's id → 404.
- `DELETE /api/v1/bank-accounts/{id}` → soft delete: set `is_active=false`, return 204. Another user's id → 404.

Response shape for an account: `{ "id", "bankId", "currencyId", "accountLast4", "nickname", "accountType", "isActive" }`.

### Profile (auth required)
- `GET /api/v1/profile` → `{ "email", "displayName", "dateOfBirth", "sexAtBirth", "heightCm", "weightKg", "timezone" }`
- `PUT /api/v1/profile` → body has the fields `displayName, dateOfBirth, sexAtBirth, heightCm, weightKg, timezone`. Validation: `sexAtBirth` one of `male`, `female`, `other` or null; `heightCm` between 50 and 250 or null; `weightKg` between 20 and 400 or null; `timezone` must be a valid Java `ZoneId`; `dateOfBirth` must be in the past. Update `updated_at`. Return the same shape as GET.
- `DELETE /api/v1/account` → hard delete the user row. Use a `@Modifying @Query("delete from User u where u.id = :id")`. The database cascade removes `user_profile`, `bank_accounts`, `refresh_tokens`. Return 204.

## A8. Dockerfile (Render)

`backend/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx350m"
CMD ["java", "-jar", "app.jar"]
```

## A9. Backend tests (Testcontainers Postgres)

Write tests for all of these. All must pass.

1. Signup then login works; signup with an existing email → 409.
2. Refresh returns new tokens, and the OLD refresh token no longer works.
3. **Reusing an already-rotated refresh token → 401 AND the newest token of that family also stops working** (family revoked).
4. `GET /banks` and `GET /currencies` return the seeded rows.
5. Creating a bank account with a non-existent `bankId` → 400.
6. Creating more accounts than `bank_account_max_per_user` → 409 `LIMIT_REACHED` (use `ConfigService.set` to lower the cap to 1 in the test).
7. `ConfigService.set` with a value above `max_value` throws.
8. A request without a token to a protected endpoint returns **401** (not 403).
9. `DELETE /api/v1/account` leaves no row in `user_profile` for that user.

Check: `mvn test` passes. (If Docker is not available for Testcontainers, still write the tests and say so in your report.)

---

# PART B — FLUTTER APP (without onboarding)

## B1. Create the project

In `app/`: `flutter create --platforms=android,web .` then add packages: `http`, `provider`, `flutter_secure_storage`, `shared_preferences`, `video_player`. In `android/app/build.gradle` set `minSdk` to 23 (needed by flutter_secure_storage). Make sure `android/app/src/main/AndroidManifest.xml` has `<uses-permission android:name="android.permission.INTERNET"/>`.

**Do NOT add `sqflite` in Phase 1.** There is no local cache yet (it arrives in Phase 2). This keeps the web build clean.

## B2. Folder structure

```
lib/
  main.dart
  core/
    platform_capabilities.dart
    api_client.dart
    token_store.dart
    app_config.dart
  auth/        (auth_service.dart, login_screen.dart, signup_screen.dart)
  onboarding/  (Part C)
  home/        (home_shell.dart)
  profile/     (profile_screen.dart)
  bank_accounts/ (bank_accounts_screen.dart)
```

## B3. Config and platform helper

`app_config.dart`: `const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');` (10.0.2.2 is the Android emulator's address for the host machine). For web dev run with `--dart-define=API_BASE_URL=http://localhost:8080`.

`platform_capabilities.dart`:

```dart
import 'package:flutter/foundation.dart';

class PlatformCapabilities {
  static bool get isWeb => kIsWeb;
  static bool get supportsLocalDb => !kIsWeb;        // used in Phase 2
  static bool get supportsSms => !kIsWeb;            // Phase 2
  static bool get supportsNativeSteps => !kIsWeb;    // Phase 4
  static bool get supportsVideoOnboarding => kIsWeb; // Part C
}
```

Rule: no other file may call `kIsWeb` directly. They must use this class.

## B4. Token storage

`token_store.dart`: abstract class `TokenStore` with `Future<String?> get accessToken`, `Future<String?> get refreshToken`, `Future<void> save(String access, String refresh)`, `Future<void> clear()`.

- `SecureTokenStore` (mobile) uses `flutter_secure_storage`.
- `MemoryTokenStore` (web) keeps both tokens in plain variables. Do NOT use localStorage or shared_preferences for tokens. Consequence (accepted): reloading the browser tab sends the user to Login. This is intentional.
- Choose the implementation with `PlatformCapabilities.isWeb`.

## B5. API client (exact behavior)

One class `ApiClient`. Rules:

1. Adds `Authorization: Bearer <access>` and `Content-Type: application/json` to every request (except it sends no Authorization for `/api/v1/auth/*`).
2. Timeout 60 seconds per request (Render free service takes up to a minute to wake up).
3. If a response is 401 and the path does NOT start with `/api/v1/auth/` and a refresh token exists: call refresh ONCE, then retry the original request ONCE.
4. Only ONE refresh may run at a time. Use a shared `Future<bool>? _refreshing;` so parallel requests wait on the same refresh:

```dart
Future<bool> _refreshOnce() {
  _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);
  return _refreshing!;
}
```

5. `_doRefresh()` posts the refresh token to `/api/v1/auth/refresh`. On 200 it saves the new tokens and returns true. Otherwise it clears the tokens, calls the `onSessionExpired` callback (which routes to Login), and returns false.

## B6. Screens (keep them plain: default Material 3, no custom theme beyond a dark `ColorScheme.fromSeed`)

1. **Splash** (in `main.dart` via an `AuthGate` widget): order of checks:
   a. If onboarding not seen (Part C flag) → Onboarding screen.
   b. Else on mobile, if a refresh token exists → try refresh → success → Home; fail → Login.
   c. Else → Login. (On web there are never stored tokens, so this goes to Login.)
2. **Login** and **Signup**: email + password fields, a button, an error text under the form, a loading state. Signup has a link to Login and the reverse.
3. **Home shell**: bottom navigation with 3 tabs: Expenses, Fitness, Insights. Each tab body is just centered text "Coming soon". An app bar action opens Profile; another opens Bank Accounts.
4. **Profile**: loads `GET /profile`; editable fields from the profile endpoint; Save button (`PUT /profile`); a red "Delete account" button with a confirmation dialog. After delete, clear tokens and go to Login. Logout button (calls logout, clears tokens, goes to Login).
5. **Bank Accounts**: list from `GET /bank-accounts` showing nickname or bank name + last4; "+" button opens a form (bank dropdown from `GET /banks`, currency dropdown from `GET /currencies`, last4, nickname, type dropdown); edit action (PATCH); deactivate action (DELETE with confirm). Show the error message from the API when the limit is reached.

Use `provider` for state (one `AuthService` ChangeNotifier is enough). Use plain `Navigator.push` / `pushAndRemoveUntil`. Do not add go_router or other routing packages.

Check: `flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080` and an Android emulator both allow signup → home → add a bank account → profile edit → logout → login.

---

# PART C — VIDEO ONBOARDING

Build this AFTER Parts A and B work. The goal: a first-launch intro with 5 swipeable slides. On Web, swiping scrubs a pre-made video forwards and backwards (the video does NOT play by itself; the swipe position controls which frame is shown). On Android there is no video: the same slides appear over a plain dark gradient with an icon.

The video file is made by the human (AI-generated, 10 seconds, no text, one continuous shot: a dashboard "builds itself" step by step). The code must work even if the video file is missing (fall back to the no-video look and never crash).

## C1. Where the video lives (important)

Put the video file at `app/web/videos/onboarding.mp4`. It must NOT be listed under `assets:` in `pubspec.yaml`. Files in `web/` are served only in the web build, so the Android APK does not contain the video.

### Human step: prepare the video (the agent writes this into a `README_VIDEO.md` file, does not run it)

For smooth scrubbing, the video must be re-encoded so EVERY frame is a keyframe, and kept small (free hosting has a small daily download limit; aim for under 15 MB):

```
ffmpeg -i input.mp4 -an -vf "scale=1280:-2" -c:v libx264 -g 1 -keyint_min 1 -crf 26 -preset slow -pix_fmt yuv420p -movflags +faststart onboarding.mp4
```

If the output is over 15 MB, raise `-crf` to 29 or use `scale=960:-2`.

## C2. Packages and first-launch flag

Uses `video_player` and `shared_preferences` (already added in B1). Create `lib/onboarding/onboarding_flag.dart` with:

```dart
import 'package:shared_preferences/shared_preferences.dart';

class OnboardingFlag {
  static const _key = 'onboarding_seen_v1';
  static Future<bool> isSeen() async =>
      (await SharedPreferences.getInstance()).getBool(_key) ?? false;
  static Future<void> markSeen() async =>
      (await SharedPreferences.getInstance()).setBool(_key, true);
}
```

(It is fine to use shared_preferences here because this flag is not sensitive.)

## C3. The beats file (tunable numbers)

Create `lib/onboarding/onboarding_beats.dart`:

```dart
// Second marks in the video where each of the 5 slides should be "fully shown".
// TUNE THESE after the human watches the real video.
// Slide 1 = card + pulse ring landed, 2 = transactions stacked, 3 = fitness rings filled,
// 4 = budget ring wrapped, 5 = AI bubble on top.
const List<double> kBeatSeconds = [0.0, 2.5, 4.5, 7.0, 9.5];

const List<String> kSlideTitles = [
  'Your money and your health, in one place',
  'Every expense, tracked automatically',
  'Steps, water, workouts. Logged just as easily',
  'Stay inside your budget',
  'AI ties it all together',
];

const List<String> kSlideSubtitles = [
  'Connect your accounts and start tracking.',
  'Add expenses by hand, or import them.',
  'Home fitness tracking, kept simple.',
  'Set limits and see your progress.',
  'Plain-language summaries of your own numbers.',
];
```

## C4. The scrub mapping

Create `lib/onboarding/scrub_mapping.dart`:

```dart
import 'onboarding_beats.dart';

/// page: fractional page value from PageController (0.0 to 4.0).
/// videoSeconds: real duration of the video.
double pageToSeconds(double page, double videoSeconds) {
  final p = page.clamp(0.0, (kBeatSeconds.length - 1).toDouble());
  final i = p.floor();
  if (i >= kBeatSeconds.length - 1) {
    return _clamp(kBeatSeconds.last, videoSeconds);
  }
  final t = p - i;
  final a = _clamp(kBeatSeconds[i], videoSeconds);
  final b = _clamp(kBeatSeconds[i + 1], videoSeconds);
  return a + (b - a) * t;
}

double _clamp(double s, double videoSeconds) =>
    s.clamp(0.0, (videoSeconds - 0.05).clamp(0.0, double.infinity));
```

Write a small unit test for `pageToSeconds`: page 0 → first beat, page 4 → last beat, page 1.5 → halfway between beat 2 and beat 3, and a beat larger than the video duration gets clamped.

## C5. The onboarding screen

Create `lib/onboarding/onboarding_screen.dart` as a `StatefulWidget`. Required behavior:

1. State: a `PageController` with 5 pages; a nullable `VideoPlayerController? _video`; `bool _videoReady = false`.
2. `initState`: if `PlatformCapabilities.supportsVideoOnboarding`, create the controller with `VideoPlayerController.networkUrl(Uri.base.resolve('videos/onboarding.mp4'))`, then `await initialize()`, `setVolume(0)`, `setLooping(false)`, `pause()`, `seekTo(Duration.zero)`, and set `_videoReady = true`. Wrap ALL of this in try/catch: on any error, leave `_videoReady = false` (fallback look). Never call `play()`.
3. Add a listener on the `PageController` that, when `_videoReady` and the controller has clients, reads `page` (a double) and calls `_scrubTo(...)` with `pageToSeconds(page, duration)`. Guard against `page == null` before first layout.
4. Throttle seeking (a new seek must not start while one is running; keep only the latest pending target). Use exactly this logic:

```dart
bool _seeking = false;
Duration? _pending;

Future<void> _scrubTo(Duration target) async {
  if (_seeking) {
    _pending = target;
    return;
  }
  _seeking = true;
  try {
    await _video!.seekTo(target);
  } finally {
    _seeking = false;
  }
  final next = _pending;
  _pending = null;
  if (next != null) _scrubTo(next);
}
```

5. Layout (a `Stack`):
   - Bottom layer: if `_videoReady`, the video filling the screen using `FittedBox(fit: BoxFit.cover, child: SizedBox(width: v.size.width, height: v.size.height, child: VideoPlayer(_video!)))` inside `SizedBox.expand`. Otherwise a dark vertical gradient (dark navy to near-black) with a large centered icon for the current slide (use `Icons.account_balance_wallet`, `Icons.receipt_long`, `Icons.directions_run`, `Icons.pie_chart`, `Icons.auto_awesome`) — pick by the rounded current page.
   - A dark semi-transparent gradient at the bottom (so text is readable over the video).
   - Top layer: a `PageView` (using the controller, 5 pages) whose pages are transparent and show the title (large) and subtitle (smaller) aligned near the bottom.
   - A row of 5 dots showing the current page.
   - A "Skip" text button at the top right (visible on slides 1–4).
   - A bottom button: "Next" on slides 1–4 (calls `animateToPage(next, duration: 600ms, curve: Curves.easeInOut)`, which scrubs the video smoothly); "Get started" on slide 5.
6. "Skip" and "Get started" both call `OnboardingFlag.markSeen()` then replace the screen with the Login screen (`Navigator.pushReplacement`).
7. `dispose`: dispose the `PageController` and the video controller.

Do not use a `ScrollController`. The fractional `PageController.page` is the only progress source.

## C6. Wire it in

In `AuthGate` (B6 step 1a): `await OnboardingFlag.isSeen()`; if false, show `OnboardingScreen`.

## C7. Onboarding checks

- Web, with NO video file present: onboarding shows the gradient + icons and swiping works. No crash, no red error screen.
- Web, with the video present: dragging the slides forward/backward moves the video forward/backward in step with the finger. Tapping Next animates the video smoothly. The video never plays by itself.
- Android: same 5 slides, no video, no extra download of the video (confirm `app/web/videos/` is not in `pubspec.yaml`).
- After "Get started" or "Skip", restarting the app goes directly to Login (the onboarding does not show again).
- The web build compiles.

---

# PART D — DEPLOY (free)

## D1. Neon
Create a free Neon project. Copy the connection details (use the NON-pooled host). Build `DB_URL` as the JDBC URL (see A2).

## D2. Render (backend)
New Web Service → connect the GitHub repo → Root Directory `backend` → Runtime Docker → Instance type Free. Set the environment variables from A2 (`CORS_ALLOWED_ORIGINS` = the Firebase URL from D3; add it after D3). Set the health check path to `/actuator/health`. Flyway runs on boot.

## D3. Firebase Hosting (web)
In `app/`: `flutter build web --dart-define=API_BASE_URL=https://<your-render-service>.onrender.com`. Then `firebase init hosting` (public directory `build/web`, single-page app = yes, no GitHub deploys) and `firebase deploy --only hosting`. Use the free Spark plan; do not enable billing. Copy the hosting URL and put it in Render's `CORS_ALLOWED_ORIGINS` (also redeploy Render). The hosting URL is for example `https://<project>.web.app`; include both `.web.app` and `.firebaseapp.com` origins, comma separated.

## D4. Android APK
`flutter build apk --release --dart-define=API_BASE_URL=https://<your-render-service>.onrender.com`. Install the APK on a device. (Firebase App Distribution is Phase 6; for now just sideload.)

---

# 8. DEFINITION OF DONE

- [ ] `V1__phase1_foundation.sql` applied on a fresh Neon database; seeded banks, INR, and config key exist
- [ ] Signup, login, refresh (with rotation), logout all work
- [ ] Reusing a rotated refresh token revokes the family and returns 401 (tested)
- [ ] Protected endpoints return 401 (not 403) without a token
- [ ] Banks, currencies, bank-accounts CRUD (soft delete, FK checks, config cap), profile, delete account all work and are tested
- [ ] `mvn test` passes
- [ ] Flutter runs on an Android emulator/device and in Chrome
- [ ] Web build does not include `sqflite` (it is not in `pubspec.yaml`)
- [ ] Onboarding: 5 slides; video scrubs on web when the file exists; falls back cleanly when it does not; shown only once
- [ ] The video file is not bundled into the Android APK
- [ ] Backend deployed on Render, DB on Neon, web on Firebase Hosting, and the hosted web app can log in against the Render backend (CORS confirmed)
- [ ] Total cost so far: $0

# 9. OUT OF SCOPE (do not build)

SMS parsing, transactions, categories, budgets (Phase 2). Statement upload and OCR (Phase 3). Fitness tables and screens (Phase 4). Anything Gemini or AI related, mood log (Phase 5). Push notifications, Firebase App Distribution setup, CORS hardening (Phase 6). Local SQLite cache (Phase 2). Replay-onboarding button, theming work, animations other than the onboarding video scrub.

When every box in section 8 is checked, Phase 1 is done and Phase 2 can start.
