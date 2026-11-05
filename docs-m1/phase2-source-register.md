# Phase 2 — Islamic Content Source Register (M1)

**Version:** 0.1 · **Date:** 2026-11-02 · **Status:** For M1 owner review
**Scope:** Phase 2 deliverable — candidate content sources, access paths, licensing status and restrictions. **No content has been imported.** Development uses synthetic data only (owner authorisation 4; standing restriction: no unlicensed Islamic content).
**Verification method:** licensor pages, license databases and repository license files consulted on 2026-11-02 (citations in the register). "Verified" = license text read directly from the licensor or a maintained license database this cycle; "Conditional" = usable under documented terms that require an action (key request, written consent, attribution plan); "Unverified" = source is a candidate but its terms could not be confirmed yet.
**Gate linkage:** production imports are bound by **OD-05 (gate G-3)** — nothing below authorises an import. Each row lists the next action required *before* any import.

---

## 1. Quran — Arabic text

| ID | Source | Access | License status | Key terms (verified 2026-11-02) | Next action before import |
|---|---|---|---|---|---|
| SR-01 | **Tanzil Quran Text** (Uthmani + Simple scripts, v1.1) | Bulk download (text/XML/SQL), also verse API mirrors | **Verified — CC Attribution 3.0** | Verbatim copies only — *changing the text is not allowed*; credit "Tanzil Project" + link to tanzil.net; include the copyright notice in copies and derived files ([text license](https://tanzil.net/docs/text_license), [download](https://tanzil.net/download)) | Import pipeline must store text verbatim + render attribution; structural QA (6,236 ayah count) at import (docs/04 §3.3) |
| SR-02 | **Quran Foundation / quran.com APIs** | REST API (+ Content Sync for offline) | **Conditional — proprietary developer terms** | Display of QF Content allowed unmodified; no redistribution as data; cache ≤ 7 days except via Content Sync; **no machine-learning use without written consent** ([developer terms](https://api-docs.quran.foundation/legal/developer-terms)); quran.com ToS forbids scraping ([terms](https://quran.com/terms-and-conditions)) | Decide API-vs-bulk strategy; **written consent required before any QF content touches the M12 RAG pipeline** |
| SR-03 | **KFGQPC Uthmanic script fonts** (HAFS; QPC V2 page-fonts) | Font files (TTF/WOFF/woff2); official + mirrors | **Verified — free EULA with conditions** | Free to use/copy/distribute; **no selling, modifying, reverse engineering**; property of King Fahd Glorious Quran Printing Complex © 2010, ISBN 978-603-8010-15-0 ([ScanCode LicenseDB](https://scancode-licensedb.aboutcode.org/kfgqpc-uthmanic-script-hafs.html); community mirror [nuqayah/qpc-fonts](https://github.com/nuqayah/qpc-fonts); QPC V2 via [qul.tarteel.ai](https://qul.tarteel.ai/resources/font/249)) | Bundle font + EULA notice; prefer official download source; per-resource terms check on qul.tarteel.ai resources |

## 2. Quran — translations (EN / UR / HI)

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-04 | **Tanzil Translations** (incl. EN Pickthall/Saheeh Intl/Itani…, UR Jalandhry/Maududi, multi-language) | Bulk download | **Conditional — non-commercial grant** | "For **non-commercial purposes only**; otherwise permission from translator/publisher"; using >3 translations requires a link back to the translations page; redistributing the list elsewhere needs Tanzil permission ([translations](https://tanzil.net/trans)) | Confirm app monetisation model with owner; if freemium/ads ⇒ treat as commercial ⇒ obtain translator/publisher permissions for copyrighted translations |
| SR-05 | Public-domain translations (e.g. **Pickthall 1930**, EN) | via Tanzil files / PD repositories | **Conditional — PD basis; per-file confirmation required** | Pickthall (d. 1956; work published 1930) is public domain — relied on by existing apps ([attribution example](https://myquran.io/licenses)); confirm each chosen file's edition provenance | Select PD set for guaranteed commercial safety; verify no later-revised edition (revisions may carry new copyright) |

## 3. Word-by-word (M02) & Arabic corpus data

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-06 | **Quranic Arabic Corpus** (corpus.quran.com — word-by-word grammar) | Data download page | **Unverified — copyleft review needed** | Corpus download page states "available under the **GNU public license** with terms of use" (retrieved 2026-11-02). GPL terms on a dataset need careful interpretation (share-alike scope for DB dumps) | Legal review of GPL-as-data; alternatively negotiate with the corpus maintainers; do not import before review |
| SR-07 | QPC word-by-word glyph/script resources (qul.tarteel.ai) | JSON/fonts per word/ayah | **Unverified — per-resource terms** | Resource portal with per-resource licence pages ([example](https://qul.tarteel.ai/resources/font/249)) | Review each resource's licence page; record in `licences` table at import time |

## 4. Hadith (M03)

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-08 | **fawazahmed0/hadith-api** (bulk JSON on CDN; multiple languages & grades; six collections + more) | Static JSON endpoints / dataset download | **Verified — The Unlicense (public domain)** | LICENSE file: unencumbered public-domain dedication — private/commercial use, modification, distribution all permitted ([LICENSE](https://github.com/fawazahmed0/hadith-api/blob/1/LICENSE)); maintainer confirms texts PD ([issue #129](https://github.com/fawazahmed0/hadith-api/issues/129)) | Our own scholarly QA: verify numbering against printed editions + grading provenance before production (approved workflow C-13); register as `ai_sources` eligible (PD) |
| SR-09 | **sunnah.com** (official API, verifiable numbering) | REST API | **Conditional — API key + terms** | API key by request via their GitHub repo; offline dump "not available yet"; site "About" covers reproduction/copying/scraping ([developers](https://sunnah.com/developers), [about](https://sunnah.com/about)); community reports of restricted access ([r/islam thread](https://www.reddit.com/r/islam/comments/1jb8ktt/sunnahcom_is_not_granting_access_to_their_api_any)) | Submit API-key request early (long lead time); obtain written usage terms; keep as cross-check source even if not bulk-imported |
| SR-10 | **Dorar.net** (grades, takhrij) via community API | REST (unofficial) | **Unverified** | API code repo exists ([dorar-hadith-api](https://github.com/AhmedElTabarani/dorar-hadith-api)); data rights remain with dorar.net — code license ≠ data license | Contact dorar.net for data terms before any use |

## 5. Tafsir (M02: Ibn Kathir, al-Tabari, al-Qurtubi, al-Jalalayn)

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-11 | **Tafsir al-Jalalayn (Arabic)** via Tanzil translations list | Bulk download | **Conditional — Tanzil non-commercial terms** | Included in Tanzil's translation/tafsir list under the same non-commercial grant as SR-04 ([translations](https://tanzil.net/trans)) | Same monetisation check as SR-04 |
| SR-12 | Ibn Kathir (Arabic original = PD-era work; EN abridged = copyrighted), al-Tabari, al-Qurtubi (Arabic editions) | Various (publishers, archives) | **Unverified per edition** | Classical texts are old enough to be PD, but *modern editions/translations* carry their own copyrights; provenance per edition is decisive | For each edition: identify translator/publisher + licence; prefer PD Arabic originals + licensed EN translations; record in `licences` |

## 6. Recitation audio (M02)

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-13 | **mp3quran.net** (surah-level, many reciters, API available) | MP3 downloads + API | **Unverified** | Download portal with API ([site](https://www.mp3quran.net/eng/khalf/downloads)); redistribution terms not found on public pages this cycle | Contact mp3quran.net for redistribution terms; per-reciter rights list |
| SR-14 | **quranicaudio.com** (surah-level, high quality) | MP3 downloads | **Unverified per reciter** | Free streaming/downloads ([site](https://quranicaudio.com/download/13282)); per-reciter licensing not published centrally | Obtain per-reciter permission matrix before import |
| SR-15 | **everyayah.com** (verse-level) | MP3 per-ayah | **Unverified — broken license reference** | Points to a license at versebyversequran.com/site/license which **no longer exists** ([quran.com support thread](https://quran.zendesk.com/hc/en-us/community/posts/360039393072-Missing-license-page)) | Do **not** import; either reconfirm rights with rights-holders or choose a verse-level alternative whose license can be verified |

## 7. Duas & Adhkar (M09)

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-16 | **Hisn al-Muslim** (Sa'id al-Qahtani) | Book/app data | **Unverified — publisher copyright** | Widely republished; original book copyright held by author/publisher; app-store ecosystem republishes without clear grants | Seek written permission from the rights-holder, or source duas individually with verified references (schema requires `reference_source NOT NULL` where authenticity claimed — docs/04 §3.7) |

## 8. Utilities, dictionaries & other reference data

| ID | Source | Access | License status | Key terms | Next action before import |
|---|---|---|---|---|---|
| SR-17 | **Adhan** prayer-time calculation (M08) | Java/Kotlin library | **Verified — MIT** | MIT license; high-precision Meeus algorithms; test-data repo for cross-validation ([repo](https://github.com/batoulapps/Adhan), [adhan-kotlin](https://github.com/batoulapps/adhan-kotlin)) | Adopt in backend M08 phase; keep LICENSE + attribution |
| SR-18 | **Wiktionary** dumps (M06 dictionary seed) | JSONWIKTIONARY/dumps | **Conditional — CC BY-SA 4.0 (+GFDL)** | Attribution + **share-alike** on derivatives; dual GFDL complicates merged DBs (well-established licensing; verify current dump terms at import) | Decide share-alike strategy for the dictionary DB; attribute per guidelines |
| SR-19 | Hijri calendar conventions (Umm al-Qura etc.) + calculation methods | Reference tables | **Verified — reference data (no content license required)** | Pure reference/config data (docs/04 §3.8); no religious text involved | Ship as repeatable migration when M08 lands |

## 9. AI subsystem constraints (M12 — for the record)

- The RAG corpus must be a **subset of the licensed register** (`ai_sources` rows link to licences permitting RAG — blueprint C-08, docs/04 §3.11).
- **Quran Foundation content (SR-02) may not be used to build ML models without written consent** — the AI module is explicitly excluded from QF content until consent is obtained (OD-07 / gate G-5 must also pass).
- PD (Unlicense) hadith dataset (SR-08) is currently the **only AI-eligible verified source**.
- All AI answers cite retrieved chunks only; source versions recorded (`ai_citations → ai_chunks → ai_sources.version`).

## 10. Register summary (v0.1.1 — reconciled with the CSV)

v0.1.1 correction: the three records the owner flagged are now normalised so that
the CSV and this summary agree 1:1 — **SR-05** moved from the ambiguous
"Verified-PD basis" into **Conditional** (a PD *basis* is verified, but the
required action — per-file edition confirmation — still gates it);
**SR-11** was Conditional in the CSV but was miscounted in the old summary;
**SR-19** is pure reference/config data and is counted as **Verified** in both
files (it was wrongly listed under Unverified here before). Status strings are
now identical in the CSV and this document (checked by
`scripts/verify_source_register.py`).

| Status | Count | Records |
|---|---|---|
| Verified | 5 | SR-01 (Quran text), SR-03 (fonts), SR-08 (hadith, Unlicense), SR-17 (Adhan library), SR-19 (reference data — no content license required) |
| Conditional | 6 | SR-02 (QF developer terms), SR-04 (Tanzil non-commercial), SR-05 (PD basis; per-file confirmation), SR-09 (API key + terms), SR-11 (Tanzil non-commercial), SR-18 (CC BY-SA + GFDL) |
| Unverified | 8 | SR-06, SR-07, SR-10, SR-12, SR-13, SR-14, SR-15, SR-16 |
| **Total** | **19** | reconciles 1:1 with the 19 CSV rows (5 + 6 + 8 = 19) |

**Standing rule:** nothing is imported until its row is Verified/Conditional-with-action-complete AND gate G-3 (OD-05) passes. Development remains on synthetic data (owner authorisation 4).
