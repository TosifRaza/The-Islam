# M4 Changelog

Backend
- V5 migration + M4 reference seeds (Flyway forward-only).
- New packages: content, quran, hadith, dictionary, dua, calendar, daily,
  home, search, personal, usercontent, prayer.
- Controlled import runner (manifest + sha256 verification) and integrity
  service; import/integrity admin endpoints.
- Public GET whitelist extended for knowledge content; GET /library/books/{id}
  now returns files[] (M3 gap closed, additive).
- MediaService: personal_pdf/personal_epub/personal_cover purposes.
- Security hardening kept: ownership re-check per personal-library access,
  ref-key regex validation, recitation publish block, fixture gate.
- Removed a stale M1 leftover (web/GlobalExceptionHandler) that conflicted
  with the approved M2 error contract (was already absent in the M2 ZIP).

Admin
- Content section (8 tabs) + api/content.ts + ContentPage tests; route/nav
  guards on content.edit.

Android
- Room v2 + ContentDao; ContentRepository/UserContentRepository/
  PersonalLibraryRepository; ContentApi + ContentDtos.
- Screens: surah list, quran reader (translation/tafsir/words, bookmark/
  copy/share, progress), hadith collections→books→hadiths, global search,
  dictionary, duas, calendar, utilities hub, prayer times (Adhan),
  qibla (CompassView), tasbih (offline Room), shared PDF/EPUB reader,
  personal library (upload/edit/delete/collections/open).
- Home dashboard upgraded (hijri, daily content cards, 6 quick utilities).
- Book detail "Read" action → reader; Library gains a private "My Library" entry.
- Vendored MIT Adhan v1.2.0 (LICENSE kept); JVM tests for Adhan vectors,
  Qibla bearings, Hijri (JDK cross-check), EPUB parser.
- Known issue fixed during M4: session data-loss event rebuilt from source;
  all artefacts re-verified afterwards (see evidence report).
