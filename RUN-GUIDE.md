# The Islam — Run Guide
## How to run in Android Studio + How to run on your phone

*Applies to: source snapshot `0.5.0-m5-fix6` (this repo, see `git log`) and APK `ikp-app-debug-phone-usb-0.5.0-m5-fix6.apk` (versionCode 9). fix6 = fix5 + a splash double-navigation crash fix (SplashFragment), found and resolved during our emulator E2E re-verification.*

---

## Part A — Run the Android app in Android Studio

### A1. What you need installed
| Tool | Version | Notes |
| --- | --- | --- |
| Android Studio | Hedgehog (2023.1) or newer | Iguana/Koala/Ladybug all fine |
| JDK | 17 or 21 | **Android Studio's embedded JDK is fine — install nothing** |
| Android SDK | Platform 34 + Build-Tools 34 | Studio installs these automatically on first sync |
| A backend | running on your computer on port 8080 | see Part C — or skip it, the app shows an offline banner but does not crash |

### A2. Steps
1. **Unzip** `ikp-platform-source-0.5.0-m5-fix6.zip` anywhere (path without spaces is safest).
2. Launch Android Studio → **Open** → select the **`platform-dev/android`** folder (NOT the repo root — the Android project root is the `android/` subfolder).
3. Wait for **Gradle sync** to finish (first sync downloads dependencies — a few minutes; Gradle 8.9 + AGP 8.5.2 are downloaded automatically by the wrapper).
   - If your network is slow/blocked, create the file `~/.gradle/init.gradle` (Linux/macOS) or `C:\Users\<you>\.gradle\init.gradle` (Windows) with a mirror:
     ```groovy
     allprojects {
         repositories {
             maven { url 'https://maven.aliyun.com/repository/google' }
             maven { url 'https://maven.aliyun.com/repository/central' }
             google()
             mavenCentral()
         }
     }
     ```
4. **Run the backend first** (Part C below) — start it and keep it running.
5. Pick a device:
   - **Emulator:** create any AVD with API 30+ in Device Manager. The app's default API URL is `http://10.0.2.2:8080/` — that is the emulator's alias for *your computer's localhost:8080*, so **no configuration needed**: just press **Run ▶ (Shift+F10)**.
   - **Your phone via USB:** enable Developer options → USB debugging, plug in, pick your phone in the device dropdown, press **Run ▶**. Then run `adb reverse tcp:8080 tcp:8080` once (see Part B) because a build from Studio keeps the default `10.0.2.2` URL which only works on emulators.
     - Alternative for USB builds from Studio: add `-PIKP_BASE_URL=http://127.0.0.1:8080/` to the Gradle build (Settings → Build Tools → Gradle → Command-line options, or run `./gradlew installDebug -PIKP_BASE_URL=http://127.0.0.1:8080/`), then `adb reverse` makes it work exactly like the prebuilt APK.
6. Log in with a dev account (table below), e.g. `dev-reader@ikp.test` / `Dev#Reader-1234`.

### A3. Run the unit tests
`./gradlew test` in the `android/` folder — the suite is 52/52 green at this snapshot.

---

## Part B — Run the app on your phone (prebuilt APK)

The shipped **`ikp-app-debug-phone-usb-0.5.0-m5-fix6.apk`** is built with the API URL `http://127.0.0.1:8080/`, which works over **USB** using adb's port-forwarding (`adb reverse`). This is a debug build for development/testing.

### B1. One-time phone setup (OnePlus CPH2585 / any Android 8+)
1. **Settings → About device → Version → tap "Build number" 7 times** → Developer options unlocked.
2. **Settings → Additional settings → Developer options → enable "USB debugging"**.
3. Install **adb** on your computer:
   - Windows: download "SDK Platform Tools" from developer.android.com/tools/releases/platform-tools, unzip, open a terminal in that folder.
   - macOS: `brew install android-platform-tools` · Linux: `sudo apt install adb`.
4. Plug the phone in with a data USB cable → the phone asks "Allow USB debugging?" → **Allow** (tick "Always allow from this computer").
5. Check: `adb devices` → must list your phone as `device`.

### B2. Start the backend on your computer
Follow **Part C** and keep it running. Verify: open `http://127.0.0.1:8080/actuator/health` in a browser → `{"status":"UP"}`.

### B3. Install and connect (every session)
```bash
adb install -r ikp-app-debug-phone-usb-0.5.0-m5-fix6.apk   # install/update the app
adb reverse tcp:8080 tcp:8080                              # phone's localhost:8080 -> your computer's 8080
```
4. Launch **The Islam** from the launcher.
5. Log in: `dev-reader@ikp.test` / `Dev#Reader-1234` (or any account from the table below).

**Notes**
- Run `adb reverse` again after every phone reboot or re-plug — it does not survive reconnection.
- If the backend is stopped you'll see the **"You are offline"** banner inside the app — the app stays alive (that resilience is exactly what fix4/fix5 verified).
- To remove: `adb uninstall com.ikp.dev.app`.

---

## Part C — Run the backend on your computer

Two supported ways. Both auto-create the database schema (Flyway) and, with `IKP_SEED_DEV_DATA=true`, the 8 dev accounts.

### Option 1 — Docker (easiest, runs backend + admin + DB)
In the unzipped `platform-dev/` folder (requires Docker Desktop):
```bash
docker compose up --build
```
- Backend: `http://localhost:8080` · Admin web: `http://localhost:5173` · PostgreSQL: `localhost:5432` (db `ikp`, user `ikp`, password `ikp_dev_password`).

### Option 2 — Manual (JDK 17+ and Maven)
1. Install **PostgreSQL 14+**, then create the database:
   ```sql
   CREATE USER ikp WITH PASSWORD 'ikp';
   CREATE DATABASE ikp OWNER ikp;
   ```
2. Build the jar (in `platform-dev/backend/`):
   ```bash
   mvn -DskipTests package
   ```
3. Run it (defaults already match the SQL above — `localhost:5432/ikp`):
   ```bash
   IKP_SEED_DEV_DATA=true java -jar target/ikp-backend-0.1.0-SNAPSHOT.jar --server.port=8080
   ```
   (Windows PowerShell: `$env:IKP_SEED_DEV_DATA="true"; java -jar target\ikp-backend-0.1.0-SNAPSHOT.jar --server.port=8080`)
4. Verify: `http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`.

Optional env overrides (what our own sandbox used): `IKP_DB_URL`, `IKP_DB_USER`, `IKP_DB_PASSWORD`, `IKP_STORAGE_ROOT`, `IKP_IMPORT_DIR`.

---

## Dev accounts (created automatically by the dev seeder)

| Email | Password | Role |
| --- | --- | --- |
| dev-superadmin@ikp.test | Dev#Super-1234 | super_admin |
| dev-admin@ikp.test | Dev#Admin-1234 | admin |
| dev-content@ikp.test | Dev#Content-1234 | content_manager |
| dev-scholar@ikp.test | Dev#Scholar-1234 | scholar_reviewer |
| dev-support@ikp.test | Dev#Support-1234 | support_manager |
| dev-academy@ikp.test | Dev#Academy-1234 | academy_manager |
| dev-reader@ikp.test | Dev#Reader-1234 | registered_user |
| dev-reader2@ikp.test | Dev#Reader-5678 | registered_user |

Login API: `POST /api/v1/auth/login` with `{"email":"...","password":"...","deviceName":"..."}` → `data.accessToken`.

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| "You are offline" banner in app | Backend not running, or `adb reverse tcp:8080 tcp:8080` not applied after replug |
| `adb: no devices` | Cable is charge-only or authorization popup pending — try another cable, re-allow debugging |
| Install failed `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An older IKP build with the same id exists: `adb uninstall com.ikp.dev.app`, then install again |
| Gradle sync fails on SDK licenses | In Studio: Settings → Languages & Frameworks → Android SDK → accept licenses, or run `sdkmanager --licenses` |
| Login returns 401 | Passwords are case-sensitive; use the exact strings from the table |
| Port 8080 already in use | Start backend with `--server.port=8090` and then `adb reverse tcp:8080 tcp:8090` |
