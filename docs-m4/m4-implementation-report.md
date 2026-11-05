# M4 Implementation Report — Core Islamic Knowledge Modules

## Scope delivered (all nine modules)

1. **Home** — aggregated dashboard API (`GET /api/v1/home`: hijri today,
   upcoming events, recommended books, popular subjects, quick utilities,
   authenticated continue-reading/recently-read rails) + daily-content slots
   (`GET /api/v1/home/daily`, published records only, deterministic rotation).
   Android home rebuilt: hijri line, daily ayah/hadith/reminder/event cards,
   6 quick-utility shortcuts (all navigate to real modules), books/subjects rails kept.
2. **Quran & Tafsir** — 114 surahs + 6,236 ayahs imported verbatim (SR-01,
   manifest + sha256 verified, verbatim-only guard, no update path exists);
   translations EN/UR/HI as labelled fixtures (SR-04/05 unresolved);
   word-by-word framework with labelled fixtures; tafsir framework for the
   four requested works (all fixture-gated; Jalalayn placeholder entries for
   reader-flow testing only); ayah study view (translations, words, notes);
   reader with translation picker, word mode, bookmark/favourite/copy/share,
   progress; FTS Arabic + translation search.
3. **Hadith Library** — six collections registered with edition + numbering
   provenance; Bukhari imported (7,563 rows, ara/eng/urd merged by dataset
   numbering; other five collections importable by the same admin action —
   full-corpus import is one command per collection and is exercised by the
   harness for Bukhari); collection→book→hadith navigation; gradings preserved
   per-scholar with source provenance (never merged); scholarly-QA status
   exposed; search; bookmarks; copy/share with attribution.
4. **Digital Islamic Library** — M2 book domain extended (per-format file refs
   + download-token flow, closing the M3 contract gap); reader with PDF
   (PdfRenderer, zoom, page nav, honest no-text-search note) and EPUB
   (parser without new dependencies, WebView light/dark/sepia themes, font
   size, TOC, real in-book search).
5. **Personal Knowledge Library** — private uploads (multipart → personal_*
   quarantine buckets → scan gate → owner-scoped rows), collections, edit,
   delete, signed download-token with ownership re-verification; strict
   separation from platform books (no admin endpoint; schema isolation;
   verified by tests); duplicate-file hash collision handled by media reuse.
6. **Arabic Dictionary** — lookup + entries with EN/UR/HI meaning columns,
   fixture-labelled (SR-18 unresolved), local search history (Room).
7. **Islamic Calendar** — Hijri today/month/events (JDK Umm al-Qura
   chronology), calculated-vs-confirmed labelling everywhere, convention +
   ±3-day offset handling, published events seeded (SR-19 reference data).
8. **Islamic Utilities** — Prayer Times (vendored MIT Adhan v1.2.0, verified
   against upstream reference vectors; presets/manual/device location with
   permission-denied fallback; method + madhab preferences), Qibla (Adhan
   bearing math + drawn compass + rotation-vector sensor + calibration
   guidance + static fallback), Tasbih (fully offline Room counters, daily
   totals + history, vibration/beep, long-press reset).
9. **Duas & Daily Worship** — 10 category structure + fixture entries
   (SR-16 unresolved; no religious text invented), favourites, honest audio
   disabled state.

## Cross-cutting

- **Global search** (`GET /api/v1/search`): grouped (quran, hadith, books,
  authors, subjects, duas, dictionary) + honest later-milestone notes for
  research/courses/lessons/scholars/fatwas; personal uploads structurally
  excluded.
- **Admin CMS** — new Content section (8 tabs): imports & integrity, Quran
  editions, tafsir editions, hadith collections (QA status + publish state),
  dictionary, duas, calendar events, daily content with draft→review→approve→
  publish workflow; recitation publish hard-blocked (LIC-001).
- **Database** — forward-only `V5__m4_knowledge_modules.sql` (30 tables +
  FTS indexes) + `R__seed_m4_reference.sql` (licences, conventions, events,
  prayer methods). No applied migration touched.
- **Android Room** — v1→v2 forward-only migration (surah cache, daily cache,
  dictionary history, Tasbih counters/history).
- **User content** — bookmarks/notes/favourites/progress/history APIs with
  strict ref-key validation; Android writes local-first, syncs when
  authenticated (full conflict-safe sync stays M7, documented).

## Dependencies added (all verified)

| Dependency | Version | Licence | Why |
|---|---|---|---|
| batoulapps Adhan (vendored source) | v1.2.0 | MIT (LICENSE kept) | prayer times + qibla math (SR-17 Verified) |
| none for EPUB/PDF | — | — | java.util.zip + android.graphics.pdf avoid unapproved deps |

## What is deliberately NOT in M4 (honest)

- Real Quran translations / word-by-word / tafsir / dictionary / dua texts
  (rights unresolved — labelled fixtures only, publish-gated).
- Recitation audio (rights unverified — no import, no download, publish blocked).
- Full hadith corpus rows for the five non-Bukhari collections in the demo DB
  (importable via the same controlled-import command; integrity report
  records per-collection import state).
- Instrumentation test execution (no emulator/KVM — compiled only).
- Full offline two-way sync (M7 per roadmap).
