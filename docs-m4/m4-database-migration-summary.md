# M4 Database Migration Summary

Forward-only, per the approved policy. No applied migration modified.

## New versioned migration
`content/V5__m4_knowledge_modules.sql` — 30 tables:
import_batches · quran_surahs · quran_ayahs · quran_translation_editions ·
quran_ayah_translations · quran_word_entries · tafsir_editions · tafsir_entries ·
ayah_study_notes · hadith_collections · hadith_books · hadiths ·
dictionary_entries · dua_categories · duas · hijri_conventions · islamic_events ·
daily_content_items · recitation_editions · ayah_audio · personal_books ·
personal_collections · personal_book_items · user_bookmarks · user_notes ·
user_favourites · reading_progress · reading_history · prayer_calculation_methods
(+ FTS GIN indexes on quran text/translations/hadith ar+en/dictionary/duas/books).

## New repeatable seed
`content/R__seed_m4_reference.sql` — licence rows (cc_by_30_tanzil,
unlicense_hadith, dev_fixture_unresolved, all_rights_unverified), hijri
conventions, 8 published Islamic events (basis=calculated), 9 prayer
calculation methods.

## Android Room
v1→v2 forward-only `Migration(1,2)`: quran_surahs_cache, daily_content_cache,
dictionary_history, tasbih_counters, tasbih_daily_history. Destructive
fallback remains disabled.

## Provenance & integrity
`import_batches` records source register id, licence, sha256, manifest JSON,
counts and integrity status for every import; the integrity report validates
DB state against those manifests at any time.
