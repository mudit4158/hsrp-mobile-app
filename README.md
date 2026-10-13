# hsrp-dms-android — HSRP Dealer app

The dealer Android app of the HSRP Dealer Management System prototype (Kotlin, Jetpack Compose). Dealers sign in,
place and follow orders, inward delivery challans plate by plate, and record fitment with photos. Inward and fitment
work offline: they are queued on the phone and sent when the connection returns.

| Repo | What |
| --- | --- |
| [hsrp-dms-be](../hsrp-dms-be) | API, database, mocks, seed, contracts, decision log — **start here** |
| [hsrp-dms-fe](../hsrp-dms-fe) | Web app |
| **hsrp-dms-android** (this) | Dealer app |

Open items: [docs/TRACKER.md](docs/TRACKER.md) and the [backend tracker](../hsrp-dms-be/docs/TRACKER.md) ·
decisions: [hsrp-dms-be/docs/DECISIONS.md](../hsrp-dms-be/docs/DECISIONS.md) · working in the repo: [AGENTS.md](AGENTS.md).

## Build

Needs JDK 17 and the Android SDK (Android Studio installs it). Create `local.properties` if it is missing:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

Then:

```bash
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Run

Start the backend first (in `../hsrp-dms-be`: `docker compose up -d`).

**Emulator:** start an emulator, then

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app's default server is `http://10.0.2.2:3000`, which is the host computer as seen from the emulator.

**Phone on the same Wi-Fi:** the API listens on `0.0.0.0`, so the phone can reach it at the computer's LAN address.

1. Find the address. Windows: `ipconfig` → "IPv4 Address" of the Wi-Fi adapter (e.g. `192.168.1.23`). macOS: `ipconfig getifaddr en0`. Linux: `hostname -I`.
2. Allow inbound TCP 3000 in the computer's firewall (Windows asks when Docker first publishes the port; allow for private networks).
3. In the app, tap **Server:** on the sign-in screen (or Settings after sign-in), enter `http://192.168.1.23:3000`, then **Test connection**.

Plain HTTP is allowed in the **debug** build only; a release build requires HTTPS.

Sign in as `dealer.wb01` or `dealer.od02` (password = `SEED_PASSWORD` from the backend's `.env`).

## What the app does

| Tab | What |
| --- | --- |
| Home | Counts (to inward, to fit, in production, waiting for VAHAN), wallet, today's appointments; New order, Settings, Sign out |
| Orders | Search and follow orders; New order (checked with VAHAN, priced, paid from the dealer wallet) |
| Inward | Challans in transit; accept each plate or reject it with a reason |
| Fitment | Search by registration → laser codes → confirm → front and rear photos (chassis optional) → submit. The server reads the registration number on each plate photo (OCR) and refuses the fitment if it does not match. |
| Sync | The offline queue: Pending, Done or Failed with the reason; Sync now |

Debug builds also show "Test photo" in the camera for emulators without a camera: it draws a plate with the order's
registration number; "Test photo (wrong number)" draws another number to show the OCR refusal.

## Keeping the docs current

[AGENTS.md](AGENTS.md) (`CLAUDE.md` imports it), the **maintain-docs** skill in `.claude/skills/maintain-docs/`,
`scripts/docs-check.mjs` (needs Node) and the pre-commit hook in `.githooks/` keep the docs current (decision D41).
Enable the hook once per clone:

```bash
git config core.hooksPath .githooks
```
