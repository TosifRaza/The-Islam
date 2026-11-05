# M5 Device-Testing Checklist (pending emulator/device run)

Environment honesty: this sandbox has no emulator/KVM. Android instrumentation
tests **compiled** (`assembleDebugAndroidTest`) but were **not executed** —
same limitation recorded in M3/M4. This checklist is the ready-to-run list for
the first device session, mirroring docs-m4 plus the M5 surface.

## Setup
- [ ] Install `ikp-android-v0.5.0-m5-debug.apk`; point `PIKP_BASE_URL` build at the live stack (or 10.0.2.2:8080 emulator loopback)
- [ ] Boot stack with `IKP_SEED_DEV_DATA=true` + Quran import (harness recipe)

## Academy
- [ ] Academy tab: catalogue renders published course; guest sees sign-in hint (no fake member rows)
- [ ] Course detail: chapters/lessons listed; instructor credentials line only when recorded
- [ ] Enrol (signed-in) → progress appears; complete lesson → 100% + status completed; continue-rail shows next lesson
- [ ] Anonymous enrol attempt → stable error message (AUTH-002 family), no crash

## Assessments
- [ ] Quizzes list → start attempt: questions WITH options render; no correctness markers anywhere
- [ ] Answer + submit → score/pass state + per-answer explanations
- [ ] Attempt history visible; weak-topics section reflects wrong answers

## Flashcards
- [ ] Admin decks tab lists published decks; practice flip works; private deck creation behind sign-in; stranger decks never visible

## Research
- [ ] Articles / Scholars / Fatwas mode chips switch lists; fatwa search box only in fatwa mode
- [ ] Article detail: tier badge (primary/secondary/editorial), citations render from stored records
- [ ] Scholar detail shows verification status; unverified never presented as endorsed
- [ ] Fatwa detail: disclaimer always visible; reviewing scholar + date shown
- [ ] Save/note prompts sign-in when guest (backend 401 surfaced via stable code)

## Regression sweep
- [ ] M4 flows intact: Quran reader, Hadith, Duas, Calendar, Prayer/Qibla, Tasbih, personal library privacy
- [ ] Bottom bar 6 tabs; per-tab state restore; session expiry dialog; offline banner
- [ ] Arabic locale (values-ar M5 subset); RTL layout on research/academy screens
- [ ] Room v2 unchanged → upgrade path from M4 install keeps data
