package com.ikp.dev.app.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wire DTOs for the M4 knowledge-content APIs (Quran, Hadith, Dictionary,
 * Duas, Calendar, Home, Search, Personal Library). Field names match the
 * backend contract exactly.
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    public static final class SurahDto {
        public int id;
        @SerializedName("nameAr") public String nameAr;
        @SerializedName("nameTransliterated") public String nameTransliterated;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("revelationPlace") public String revelationPlace;
        @SerializedName("ayahCount") public int ayahCount;
        @SerializedName("bismillahPrefix") public boolean bismillahPrefix;
    }

    public static final class TranslationEditionDto {
        public String code;
        public String language;
        @SerializedName("displayName") public String displayName;
        public String translator;
        @SerializedName("isFixture") public Boolean isFixture;
        public String licence;
    }

    public static final class TafsirEditionDto {
        public String code;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("nameAr") public String nameAr;
        public String author;
        public String language;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class WordDto {
        public int position;
        @SerializedName("textAr") public String textAr;
        public String transliteration;
        @SerializedName("meaningEn") public String meaningEn;
        @SerializedName("meaningUr") public String meaningUr;
        @SerializedName("meaningHi") public String meaningHi;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class TranslationRowDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        public String text;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class TafsirRowDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        public String text;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class AyahDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        @SerializedName("textUthmani") public String textUthmani;
        public Integer juz;
        public boolean sajdah;
        @SerializedName("surahName") public String surahName;
        public List<TranslationRowDto> translation;
        public TafsirRowDto tafsir;
        public List<WordDto> words;
        public String attribution;
        @SerializedName("fixtureContent") public Boolean fixtureContent;
    }

    public static final class StudyNoteDto {
        public String kind;
        public String text;
        @SerializedName("sourceRef") public String sourceRef;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class AyahDetailDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        @SerializedName("textUthmani") public String textUthmani;
        @SerializedName("surahName") public String surahName;
        public Integer juz;
        public boolean sajdah;
        public String attribution;
        public java.util.LinkedHashMap<String, Object> translations;
        public List<WordDto> words;
        public List<StudyNoteDto> studyNotes;
        public List<TafsirEditionDto> tafsirs;
    }

    public static final class QuranSearchResultDto {
        public List<AyahSearchRowDto> arabic;
        public List<TranslationSearchRowDto> translations;
    }

    public static final class AyahSearchRowDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        @SerializedName("textUthmani") public String textUthmani;
        @SerializedName("surahName") public String surahName;
    }

    public static final class TranslationSearchRowDto {
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
        public String text;
        @SerializedName("editionCode") public String editionCode;
        @SerializedName("surahName") public String surahName;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class GradingDto {
        @SerializedName("gradedBy") public String gradedBy;
        public String grade;
        public String source;
    }

    public static final class HadithCollectionDto {
        public String code;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("nameAr") public String nameAr;
        @SerializedName("editionNote") public String editionNote;
        @SerializedName("numberingNote") public String numberingNote;
        @SerializedName("scholarlyQaStatus") public String scholarlyQaStatus;
        @SerializedName("hadithCount") public int hadithCount;
        @SerializedName("bookCount") public int bookCount;
    }

    public static final class HadithBookDto {
        public long id;
        @SerializedName("bookNumber") public int bookNumber;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("nameAr") public String nameAr;
        @SerializedName("hadithFirst") public Integer hadithFirst;
        @SerializedName("hadithLast") public Integer hadithLast;
        @SerializedName("hadithCount") public int hadithCount;
        @SerializedName("collectionCode") public String collectionCode;
        @SerializedName("collectionName") public String collectionName;
    }

    public static final class HadithDto {
        public long id;
        @SerializedName("numberInBook") public int numberInBook;
        @SerializedName("arabicNumber") public Integer arabicNumber;
        @SerializedName("textAr") public String textAr;
        @SerializedName("textEn") public String textEn;
        @SerializedName("textUr") public String textUr;
        @SerializedName("narratorChain") public String narratorChain;
        public List<GradingDto> gradings;
        public String takhrij;
        @SerializedName("referenceNote") public String referenceNote;
        @SerializedName("collectionCode") public String collectionCode;
        @SerializedName("collectionName") public String collectionName;
        @SerializedName("bookName") public String bookName;
        @SerializedName("bookNumber") public Integer bookNumber;
        @SerializedName("scholarlyQaStatus") public String scholarlyQaStatus;
    }

    public static final class DictionaryEntryDto {
        public long id;
        @SerializedName("headwordAr") public String headwordAr;
        public String root;
        @SerializedName("partOfSpeech") public String partOfSpeech;
        public String transliteration;
        public String pronunciation;
        @SerializedName("meaningEn") public String meaningEn;
        @SerializedName("meaningUr") public String meaningUr;
        @SerializedName("meaningHi") public String meaningHi;
        @SerializedName("isFixture") public Boolean isFixture;
    }

    public static final class DuaCategoryDto {
        public String code;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("nameAr") public String nameAr;
        @SerializedName("nameUr") public String nameUr;
        @SerializedName("nameHi") public String nameHi;
        @SerializedName("sortOrder") public int sortOrder;
    }

    public static final class DuaDto {
        public long id;
        public String title;
        @SerializedName("textAr") public String textAr;
        public String transliteration;
        @SerializedName("meaningEn") public String meaningEn;
        @SerializedName("meaningUr") public String meaningUr;
        @SerializedName("meaningHi") public String meaningHi;
        public String reference;
        @SerializedName("sourceAttribution") public String sourceAttribution;
        @SerializedName("authenticityNote") public String authenticityNote;
        @SerializedName("audioMediaId") public String audioMediaId;
        @SerializedName("isFixture") public Boolean isFixture;
        @SerializedName("categoryCode") public String categoryCode;
        @SerializedName("categoryName") public String categoryName;
    }

    public static final class HijriDto {
        public int year;
        public int month;
        public int day;
        @SerializedName("monthLength") public int monthLength;
    }

    public static final class CalendarTodayDto {
        public String gregorian;
        public HijriDto hijri;
        public boolean calculated;
        public int offsetDays;
        public String convention;
        public String note;
    }

    public static final class IslamicEventDto {
        public String code;
        @SerializedName("nameEn") public String nameEn;
        @SerializedName("nameAr") public String nameAr;
        @SerializedName("hijriMonth") public int hijriMonth;
        @SerializedName("hijriDay") public int hijriDay;
        public String basis;
        public Boolean isMajor;
        public String description;
        public String gregorianDate;
        public Boolean calculated;
    }

    public static final class PopularSubjectDto {
        public String slug;
        public String name;
        @SerializedName("bookCount") public int bookCount;
    }

    public static final class QuickUtilityDto {
        public String code;
        public String title;
        public String route;
        public boolean offline;
    }

    public static final class ProgressRowDto {
        @SerializedName("refKey") public String refKey;
        public Integer percent;
        public String updatedAt;
    }

    public static final class HistoryRowDto {
        @SerializedName("refKey") public String refKey;
        public String title;
        public String accessedAt;
    }

    public static final class HomePayloadDto {
        public HijriDto hijriToday;
        public String gregorian;
        public List<IslamicEventDto> islamicEvents;
        public List<LibraryDtos.BookRowDto> recommendedBooks;
        public List<PopularSubjectDto> popularSubjects;
        public List<QuickUtilityDto> quickUtilities;
        public List<ProgressDto> continueReading;
        public List<HistoryRowDto> recentlyRead;
        public List<Object> continueLearning;
        public List<Object> recommendedLessons;
    }

    public static final class AyahInSlotDto {
        @SerializedName("textUthmani") public String textUthmani;
        @SerializedName("surahName") public String surahName;
        @SerializedName("surahNameAr") public String surahNameAr;
        @SerializedName("surahId") public int surahId;
        @SerializedName("ayahNumber") public int ayahNumber;
    }

    public static final class HadithInSlotDto {
        public long id;
        @SerializedName("textAr") public String textAr;
        @SerializedName("textEn") public String textEn;
        @SerializedName("collectionName") public String collectionName;
        @SerializedName("bookName") public String bookName;
        @SerializedName("numberInBook") public int numberInBook;
        @SerializedName("scholarlyQaStatus") public String scholarlyQaStatus;
    }

    public static final class SlotDto {
        public String contentType;
        public String attribution;
        public AyahInSlotDto ayah;
        public TranslationRowDto translation;
        public HadithInSlotDto hadith;
        public IslamicEventDto event;
        public String customText;
    }

    public static final class DailyContentDto {
        public String date;
        public SlotDto ayah_of_day;
        public SlotDto hadith_of_day;
        public SlotDto daily_reminder;
        public SlotDto islamic_event;
    }

    public static final class SearchItemDto {
        public String id;
        public String title;
        public String subtitle;
    }

    public static final class SearchGroupDto {
        public int total;
        public List<SearchItemDto> items;
        public String note;
    }

    public static final class PersonalBookDto {
        @SerializedName("bookId") public String bookId;
        public String title;
        public String author;
        public String description;
        public String format;
        public String language;
        @SerializedName("fileSizeBytes") public Long fileSizeBytes;
        @SerializedName("textSearchable") public Boolean textSearchable;
        @SerializedName("createdAt") public String createdAt;
        @SerializedName("mediaId") public String mediaId;
        @SerializedName("coverMediaId") public String coverMediaId;
    }

    public static final class SignedUrlDto {
        public String url;
        public String expiresAt;
        public Long sizeBytes;
        public String format;
        @SerializedName("textSearchable") public Boolean textSearchable;
    }

    public static final class PersonalCollectionDto {
        public String collectionId;
        public String name;
        @SerializedName("bookCount") public int bookCount;
        public String createdAt;
    }

    public static final class BookmarkDto {
        @SerializedName("refKey") public String refKey;
        public String note;
        public String createdAt;
    }

    public static final class NoteDto {
        @SerializedName("refKey") public String refKey;
        public String body;
        public String updatedAt;
    }

    public static final class FavouriteDto {
        @SerializedName("refKey") public String refKey;
        public String createdAt;
    }

    public static final class ProgressDto {
        @SerializedName("refKey") public String refKey;
        public Integer percent;
        public String positionData;
        public String updatedAt;
    }
}
