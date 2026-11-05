# M4 Device / Instrumentation Testing (honest status)

Compiled but NOT executed in this environment (no emulator/KVM/Docker — same
limitation recorded in M3). Execution path: `./gradlew connectedDebugAndroidTest`
on CI or a device, plus this manual checklist:

1. Launch → splash → onboarding → home (guest).
2. Quran: surah list → reader → translation picker (fixture badge visible)
   → word mode → bookmark → share (attribution present).
3. Hadith: collections → books → hadiths → gradings display → bookmark.
4. Reader: open personal PDF (zoom, pages, no-search note) and EPUB
   (themes, font, TOC, search).
5. Personal library: upload PDF via document picker → list → open → delete.
6. Utilities: prayer times (city preset + manual), qibla compass rotation,
   tasbih counting with vibration.
7. Calendar: today card (calculated chip), offset +1 → chip changes.
8. RTL: Arabic surah/hadith text alignment; Urdu hadith text.
9. Offline: airplane mode → home shows cached daily + offline state;
   surah list served from Room cache.
10. TalkBack pass over new screens (48dp targets, labels present).
