# M4 Content Licensing Report

Ground truth: `docs-m1/phase2-source-register.md` v0.1.1 (owner-approved M1
deliverable). Per M4 rule 17 every import below was revalidated against that
register at import time (checksums + manifest verification in code).

## Imported (Verified sources only)

| Source | Register | Licence | Controls applied |
|---|---|---|---|
| Tanzil Quran Text, Uthmani v1.1 | SR-01 (Verified) | CC BY 3.0 — verbatim-only + attribution | verbatim write path only; copyright header kept with source file; in-app attribution string on every ayah response + share text; integrity QA 114/6,236 + per-surah counts; sha256 manifest |
| fawazahmed0/hadith-api v1 (ara/eng/urd × 6 collections) | SR-08 (Verified) | The Unlicense (PD) | edition + numbering provenance stored on collection rows; gradings keep per-scholar source; scholarly-QA status `pending` surfaced in API/UI; C-13 pre-production QA documented as remaining work |
| Hijri conventions + events, prayer method parameters | SR-19/SR-17 (Verified reference data) | reference data / MIT (Adhan vendored with LICENSE) | shipped as repeatable migration + vendored library |

## NOT imported (rights unresolved) — labelled fixtures + publish gates

| Content | Register | Status | M4 handling |
|---|---|---|---|
| Quran translations EN/UR/HI | SR-04/SR-05 | Conditional (non-commercial unresolved) | fixtures `is_fixture=true`, licence row `dev_fixture_unresolved`, visible "Development fixture" badge, production gate `app.content.allow-fixtures=false` hides all of them; integrity check asserts no non-fixture edition exists |
| Word-by-word | SR-06/SR-07 | Unverified (GPL-as-data review) | fixtures with placeholder meanings; no roots invented; admin PUT exists for the licensed import later |
| Tafsir (Ibn Kathir/Tabari/Qurtubi/Jalalayn) | SR-11/SR-12 | Conditional/Unverified per edition | editions registered, publish_state=disabled (Jalalayn fixture published for reader-flow testing only, clearly labelled) |
| Recitation audio | SR-13/14/15 | Unverified; SR-15 do-not-import | no import, no download path; recitation edition publish hard-blocked with LIC-001 (tested) |
| Duas (Hisn al-Muslim) | SR-16 | Unverified | categories + fixture entries with explicit no-text placeholder; `authenticity_note` NULL (no claims) |
| Dictionary | SR-18 | Conditional (CC BY-SA + GFDL share-alike decision) | fixture entries, no invented roots/meanings |

## Enforcement points (all tested)

1. `ContentImportService` — refuses imports when sha256 ≠ manifest.
2. Serving layer — every fixture-aware query carries `(is_fixture = false OR ?)`.
3. `AdminContentController.publishGuard` — LIC-001 for licence-less publishing
   and fixture publishing in production posture.
4. Recitation publish — unconditional LIC-001 while rights unverified.
5. Integrity service — asserts fixture labelling + no non-fixture translation
   editions + no published recitation.
