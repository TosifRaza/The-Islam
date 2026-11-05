# M4 API Integration Report

Base path `/api/v1`; success envelope `{"data", "meta"}`; errors RFC 7807
problem+json with stable codes. Public GET whitelist extended for knowledge
content (guest browsing per approved IA); everything else authenticated +
method-security as before.

## Public (whitelisted GET)
`/quran/surahs` · `/quran/surahs/{id}` · `/quran/surahs/{id}/ayahs` (translation,
tafsir, words, paging) · `/quran/ayahs/{s}:{a}` · `/quran/translations` ·
`/quran/tafsirs` · `/quran/search` · `/hadith/collections` ·
`/hadith/collections/{code}/books` · `/hadith/books/{id}/hadiths` ·
`/hadith/hadiths/{id}` · `/hadith/search` · `/dictionary/search` ·
`/dictionary/entries/{id}` · `/duas` (+categories/search/{id}) ·
`/calendar/today|month|events|conventions` · `/prayer/methods` · `/home` ·
`/home/daily` · `/search`.

## Authenticated (user)
`/user-content/bookmarks|notes|favourites|progress|history` (GET/PUT/DELETE,
ref-key whitelist regex) · `/personal/books` (multipart upload, list, detail,
PATCH, DELETE, download-token) · `/personal/collections` (+ add/remove book).

## Admin (role/permission-gated)
`/admin/content/import/quran` · `/admin/content/collections` ·
`/admin/content/import/hadith/{code}` · `/admin/content/fixtures` ·
`/admin/content/integrity` · `/admin/content/imports` · hadith collection /
hadith / gradings patches · quran + tafsir edition GET/POST/PATCH · words PUT ·
dictionary CRUD · dua category/dua POST+PATCH · calendar event/convention
PATCH · daily GET/POST/PATCH (workflow) · recitation POST/PATCH (publish
hard-blocked).

## Android Retrofit layer
`ContentApi` (45 endpoints) + `LibraryApi` (existing, now consumes the M4
`files[]` detail addition) — all wired through ApiClient with the M3
authenticator/interceptor stack. Live verification: the 19/19 harness + the
admin-driven import flow; on-device traffic uses the same contract.
