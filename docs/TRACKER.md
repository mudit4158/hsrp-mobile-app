# Tracker — hsrp-dms-android

**Last reviewed:** 12 Oct 2026

Items that touch only the dealer app. Items spanning repos, business questions and hardening are in the
[backend tracker](../../hsrp-dms-be/docs/TRACKER.md) (several involve this app: P-01, P-02, P-03, P-05, P-10, V-03,
H-05). Each item lives in exactly one tracker. Decisions: `../hsrp-dms-be/docs/DECISIONS.md`.

**Status:** Open · In progress · Blocked · Done — **Priority:** P1 breaks a demo flow · P2 needed before real use · P3 nice to have

## Fixes

| ID | Item | Priority | Status | Notes |
| --- | --- | --- | --- | --- |
| F-05 | Photo EXIF rotation fix not re-verified on device | P3 | Open | Fixed in `ui/Camera.kt` (`shrinkPhoto` applies the EXIF orientation) and built, but not checked on the emulator. Install, take a photo, confirm the thumbnail and the uploaded image are upright. |

## Not built yet

| ID | Item | Priority | Status | Notes |
| --- | --- | --- | --- | --- |
| N-03 | Release build and signing | P2 | Blocked | Only the debug APK exists. Needs the user's signing key; release builds also need HTTPS for the API and must drop the "Test photo" button (backend tracker P-05). |
| A-01 | API models kept in step by hand | P3 | Open | `data/Api.kt` mirrors the backend JSON by hand (D39). Generate the models from the backend's OpenAPI (`/api/docs-json`) or add a check that fails when an endpoint the app uses changes shape. |

## To verify

| ID | Item | Priority | Status | Notes |
| --- | --- | --- | --- | --- |
| V-01 | Challan inward by hand | P2 | Open | Inward was tested through the API (backend `pnpm demo:e2e` flow F) and the same offline queue was tested on the emulator with fitment, but the inward screen itself was not clicked through on a device. |
| V-02 | Real phone on the same Wi-Fi | P2 | Open | Only the emulator was tested. Follow README "Run" → phone (LAN address, firewall for port 3000, debug APK). |

## Done

| ID | Item | Commit |
| --- | --- | --- |
| F-08 | A rejected token (401) left the app on Home showing "Your session has ended" on every screen; found after the backend was reseeded. The API client now clears the session on a 401, so the app returns to sign-in; the offline queue is kept. Verified on the emulator. | "Return to sign-in on 401" commit |
| — | Split out of the monorepo with its history; own .gitignore, docs, skill and pre-commit hook (D38, D41) | "Split from the monorepo" commit |
