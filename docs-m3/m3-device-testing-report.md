# M3 Device Testing Report

Milestone: M3 · 2026-11-02

## 1. Environment status (honest)

This development sandbox provides **no Android device, no KVM-accelerated
emulator and no Docker** (`/dev/kvm` absent; `docker` not installed). In line
with prompt §22, this is **recorded as a limitation** — no on-device claim is
made beyond what was actually verified here.

## 2. What WAS verified in this environment (real outputs)

- Clean `assembleDebug` → installable debug APK (8.4 MB, `versionCode 2`).
- `assembleRelease` with R8 → unsigned release APK (2.8 MB).
- `assembleDebugAndroidTest` → instrumentation suite **compiles**.
- `testDebugUnitTest` → **28/28 pass**.
- Live API-contract integration (the app's own networking layer vs the real
  backend) → **29/29 checks pass**.

## 3. Device / emulator execution plan (ready to run)

CI (GitHub Actions, `ubuntu-latest`) and any developer machine can execute:

```bash
./gradlew connectedDebugAndroidTest          # instrumentation suite
```

or with the SDK installed locally:

```bash
avdmanager create avd -n test -k "system-images;android-34;google_apis;x86_64"
emulator -avd test -no-window &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.ikp.dev.app/.MainActivity
```

(The sandbox's M2-era CI pattern already provisions SDK packages; the M3
android job builds and tests on every push.)

## 4. Device checklist for the owner / QA (remaining items)

| Check (prompt §21/§23) | Prepared by M3 | To verify on device |
|---|---|---|
| Install + launch, splash → onboarding/login | startup policy unit-tested; launcher icons (all densities + adaptive) | run APK, first-run flow |
| Six-tab navigation + state preservation | saveState/restoreState wired | switch tabs, rotate, come back |
| Login with real backend | 29-check harness on the same contract | sign in on emulator (10.0.2.2) or device via adb-reverse |
| Profile loads real data / sessions revoke | implemented against live endpoints | observe Devices & sessions |
| Logout security | revoke + clear tested at logic level | confirm post-logout state |
| Session expiry | authenticator + expiry dialog | force-expire access token, wait for 401 |
| Offline banner + cached content | NetworkMonitor + TTL caches | airplane-mode test |
| Theme switch (light/dark/system) | AppCompatDelegate + values-night | toggle in Settings |
| RTL (Arabic) + Urdu/Hindi rendering | values-ar/hi/ur + per-app locale switch | switch language, inspect layout mirroring |
| TalkBack / 200% font scale | 48 dp targets, content descriptions, sp sizing | accessibility walkthrough |
| Network-failure resilience | error/offline states on every screen | kill backend mid-use |
| Permission denial | only benign permissions at M3 (INTERNET, ACCESS_NETWORK_STATE) | n/a — optional-permission flow arrives with M4 features |

## 5. Google Play / target-SDK note (per prompt §22)

targetSdk 34 is the development target. **The exact target SDK for a Play
submission must be re-verified against Google Play's current policy at
release time** — no Play submission is planned or attempted by this
milestone, and the production applicationId remains frozen behind OD-01.
