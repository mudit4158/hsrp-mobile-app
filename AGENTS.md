# AGENTS.md — hsrp-dms-android

**Last updated:** 12 Oct 2026

Guidance for anyone (human or coding agent) working in this repository. Read it before changing anything, and keep it
true: if you change how the app is built, run, structured or tested, update this file in the same commit.

## What this repo is

The **dealer Android app** of the HSRP Dealer Management System prototype: Kotlin, Jetpack Compose (Material 3),
Retrofit + OkHttp + Gson, Room (offline queue), WorkManager (sync), CameraX (photos), DataStore (settings). Dealers
sign in (device registered), see counts and today's appointments, place and follow orders, inward challans plate by
plate, and record fitment with front/rear (optional chassis) photos. Inward and fitment are queued on the device and
sent when online, each with a fixed `Idempotency-Key`.

| Sibling repo (under `C:\workspace\hsrp\`) | Relationship |
| --- | --- |
| `hsrp-dms-be` | The API this app calls; owns the contracts, the **decision log** and the system tracker |
| `hsrp-dms-fe` | Web app; independent of this repo |

Decisions: `../hsrp-dms-be/docs/DECISIONS.md` and the
[High-Level Design](https://claude.ai/code/artifact/d7fce61b-0fe9-4bae-a8b2-7286684b9de4).

## Layout

```
app/src/main/java/com/hsrp/dealer/
  HsrpApp.kt, MainActivity.kt   app singletons (Settings, Room db), sign-in gate
  data/Settings.kt              DataStore: API base URL, token, device id
  data/Api.kt                   Retrofit service + Kotlin models of the backend API (hand-maintained, see D39)
  data/Db.kt                    Room: pending_action (offline queue)
  sync/Sync.kt                  SyncQueue (enqueue) + SyncWorker (sends with the stored Idempotency-Key)
  ui/                           Compose screens: Login, MainScaffold (nav), Home, Orders, Inward, Fitment, Camera, More
app/src/debug/                  debug-only cleartext HTTP (network_security_config) for the local API
app/build.gradle.kts            DEFAULT_API_URL (emulator: http://10.0.2.2:3000), dependencies
scripts/docs-check.mjs          documentation checks (Node)
docs/TRACKER.md                 open items that touch only this repo
.claude/skills/                 maintain-docs skill
.githooks/                      pre-commit (docs-check)
```

## Commands

Needs JDK 17 and the Android SDK (platform 35, build-tools 34+). `local.properties` must point at the SDK, e.g.
`sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (forward slashes; it is git-ignored). The backend must be running
(`hsrp-dms-be`: `docker compose up -d`).

| Command | What it does |
| --- | --- |
| `./gradlew assembleDebug` | Build the debug APK → `app/build/outputs/apk/debug/app-debug.apk` |
| `adb install -r app/build/outputs/apk/debug/app-debug.apk` | Install on a running emulator or connected phone |
| `adb shell am start -n com.hsrp.dealer/.MainActivity` | Launch the app |
| `node scripts/docs-check.mjs` | Documentation checks (also run by the pre-commit hook) |
| `git config core.hooksPath .githooks` | Enable the pre-commit hook (once per clone) |

Demo dealer users: `dealer.wb01`, `dealer.od02` (password = backend `SEED_PASSWORD`). Never commit passwords, tokens
or signing keys.

## Conventions

- API models in `data/Api.kt` mirror the backend's JSON. When the backend changes an endpoint this app uses, update
  the data class and the Retrofit method in the same change set (D39); the backend AGENTS.md reminds the other side.
- Every write that can be retried (inward, fitment) goes through `SyncQueue` with one Idempotency-Key per record;
  never send these directly from a screen.
- Errors: `unwrap()` turns RFC 7807 problems into `ApiException`; show them with `errorText()` / `ErrorBanner`.
- Session: the OkHttp interceptor in `ApiClient` clears the stored token when an authenticated call gets 401, and
  `MainActivity` then shows the sign-in screen. Don't handle 401 per screen; queued offline actions stay in Room.
- Screens use the helpers in `ui/Common.kt` (`rememberLoad`, `LoadView`, `SectionCard`, `StatusChip`).
- Cleartext HTTP is allowed in the **debug** build only (`app/src/debug`). Release builds require HTTPS.
- The debug-only "Test photo" button exists for emulators without a camera; it must not ship in a release build.

## Safety

- Talk only to the local backend; never point the app at real external systems.
- Release signing keys belong to the user; never create or commit them.
- Never push or create remotes without explicit confirmation from the user.

## Keeping documentation current (required)

Every change that affects behaviour, setup, commands, screens, decisions or open items updates the docs **in the same
commit**. Use the **maintain-docs** skill (`.claude/skills/maintain-docs/SKILL.md`):

| You changed… | Update |
| --- | --- |
| Build, SDK, dependencies, default API URL | `README.md`, this file |
| Screens, sync behaviour, conventions | this file; README if it changes how to use the app |
| A design choice | new decision in `../hsrp-dms-be/docs/DECISIONS.md` **and** the HLD (append-only) |
| Work only in this repo (done / found / deferred) | `docs/TRACKER.md` here |
| Work spanning repos | the backend tracker |

`node scripts/docs-check.mjs` (and the pre-commit hook) checks required files, review dates, tracker IDs and statuses.
It warns when code changes without any doc change.

## Gotchas

- `local.properties`: use forward slashes; backslashes are escape characters in properties files.
- The emulator reaches the computer at `10.0.2.2`; a phone needs the computer's LAN IP and port 3000 open.
- On the emulator, screenshots right after boot can be black while the GPU starts; wait and retry.
- After the backend is reseeded (`docker compose down -v`), old tokens are rejected; the app drops back to sign-in.
- WorkManager may take ~30 s after the network returns before the sync job runs.

## Where things are tracked

- This repo's items: `docs/TRACKER.md`. Cross-repo items (P-01, P-03, P-05, P-10 involve this app): backend tracker.
