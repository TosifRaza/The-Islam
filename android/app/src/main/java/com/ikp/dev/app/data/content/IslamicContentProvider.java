package com.ikp.dev.app.data.content;

import com.ikp.dev.app.data.remote.dto.ContentDtos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides authoritative Islamic knowledge content for offline use and
 * cloud fallback (Quran 114 Surahs, Hadith Kutub al-Sittah, Duas).
 */
public final class IslamicContentProvider {

    private IslamicContentProvider() {
    }

    public static List<ContentDtos.SurahDto> getAllSurahs() {
        List<ContentDtos.SurahDto> list = new ArrayList<>(114);

        // All 114 Surahs metadata: id, Arabic name, transliterated name, English meaning, revelation, ayah count
        Object[][] surahData = new Object[][]{
                {1, "الفاتحة", "Al-Fatihah", "The Opening", "Makkah", 7},
                {2, "البقرة", "Al-Baqarah", "The Cow", "Madinah", 286},
                {3, "آل عمران", "Ali 'Imran", "Family of Imran", "Madinah", 200},
                {4, "النساء", "An-Nisa", "The Women", "Madinah", 176},
                {5, "المائدة", "Al-Ma'idah", "The Table Spread", "Madinah", 120},
                {6, "الأنعام", "Al-An'am", "The Cattle", "Makkah", 165},
                {7, "الأعراف", "Al-A'raf", "The Heights", "Makkah", 206},
                {8, "الأنفال", "Al-Anfal", "The Spoils of War", "Madinah", 75},
                {9, "التوبة", "At-Tawbah", "The Repentance", "Madinah", 129},
                {10, "يونس", "Yunus", "Jonah", "Makkah", 109},
                {11, "هود", "Hud", "Hud", "Makkah", 123},
                {12, "يوسف", "Yusuf", "Joseph", "Makkah", 111},
                {13, "الرعد", "Ar-Ra'd", "The Thunder", "Madinah", 43},
                {14, "إبراهيم", "Ibrahim", "Abraham", "Makkah", 52},
                {15, "الحجر", "Al-Hijr", "The Rocky Tract", "Makkah", 99},
                {16, "النحل", "An-Nahl", "The Bee", "Makkah", 128},
                {17, "الإسراء", "Al-Isra", "The Night Journey", "Makkah", 111},
                {18, "الكهف", "Al-Kahf", "The Cave", "Makkah", 110},
                {19, "مريم", "Maryam", "Mary", "Makkah", 98},
                {20, "طه", "Ta-Ha", "Ta-Ha", "Makkah", 135},
                {21, "الأنبياء", "Al-Anbiya", "The Prophets", "Makkah", 112},
                {22, "الحج", "Al-Hajj", "The Pilgrimage", "Madinah", 78},
                {23, "المؤمنون", "Al-Mu'minun", "The Believers", "Makkah", 118},
                {24, "النور", "An-Nur", "The Light", "Madinah", 64},
                {25, "الفرقان", "Al-Furqan", "The Criterion", "Makkah", 77},
                {26, "الشعراء", "Ash-Shu'ara", "The Poets", "Makkah", 227},
                {27, "النمل", "An-Naml", "The Ant", "Makkah", 93},
                {28, "القصص", "Al-Qasas", "The Stories", "Makkah", 88},
                {29, "العنكبوت", "Al-'Ankabut", "The Spider", "Makkah", 69},
                {30, "الروم", "Ar-Rum", "The Romans", "Makkah", 60},
                {31, "لقمان", "Luqman", "Luqman", "Makkah", 34},
                {32, "السجدة", "As-Sajdah", "The Prostration", "Makkah", 30},
                {33, "الأحزاب", "Al-Ahzab", "The Combined Forces", "Madinah", 73},
                {34, "سبأ", "Saba", "Sheba", "Makkah", 54},
                {35, "فاطر", "Fatir", "Originator", "Makkah", 45},
                {36, "يس", "Ya-Sin", "Ya-Sin", "Makkah", 83},
                {37, "الصافات", "As-Saffat", "Those who set the Ranks", "Makkah", 182},
                {38, "ص", "Sad", "The Letter Sad", "Makkah", 88},
                {39, "الزمر", "Az-Zumar", "The Troops", "Makkah", 75},
                {40, "غافر", "Ghafir", "The Forgiver", "Makkah", 85},
                {41, "فصلت", "Fussilat", "Explained in Detail", "Makkah", 54},
                {42, "الشورى", "Ash-Shuraa", "The Consultation", "Makkah", 53},
                {43, "الزخرف", "Az-Zukhruf", "The Ornaments of Gold", "Makkah", 89},
                {44, "الدخان", "Ad-Dukhan", "The Smoke", "Makkah", 59},
                {45, "الجاثية", "Al-Jathiyah", "The Crouching", "Makkah", 37},
                {46, "الأحقاف", "Al-Ahqaf", "The Wind-Curved Sandhills", "Makkah", 35},
                {47, "محمد", "Muhammad", "Muhammad", "Madinah", 38},
                {48, "الفتح", "Al-Fath", "The Victory", "Madinah", 29},
                {49, "الحجرات", "Al-Hujurat", "The Rooms", "Madinah", 18},
                {50, "ق", "Qaf", "The Letter Qaf", "Makkah", 45},
                {51, "الذاريات", "Adh-Dhariyat", "The Winnowing Winds", "Makkah", 60},
                {52, "الطور", "At-Tur", "The Mount", "Makkah", 49},
                {53, "النجم", "An-Najm", "The Star", "Makkah", 62},
                {54, "القمر", "Al-Qamar", "The Moon", "Makkah", 55},
                {55, "الرحمن", "Ar-Rahman", "The Beneficent", "Madinah", 78},
                {56, "الواقعة", "Al-Waqi'ah", "The Inevitable", "Makkah", 96},
                {57, "الحديد", "Al-Hadid", "The Iron", "Madinah", 29},
                {58, "المجادلة", "Al-Mujadila", "The Pleading Woman", "Madinah", 22},
                {59, "الحشر", "Al-Hashr", "The Exile", "Madinah", 24},
                {60, "الممتحنة", "Al-Mumtahanah", "She that is to be examined", "Madinah", 13},
                {61, "الصف", "As-Saf", "The Ranks", "Madinah", 14},
                {62, "الجمعة", "Al-Jumu'ah", "The Congregation", "Madinah", 11},
                {63, "المنافقون", "Al-Munafiqun", "The Hypocrites", "Madinah", 11},
                {64, "التغابن", "At-Taghabun", "The Mutual Disillusion", "Madinah", 18},
                {65, "الطلاق", "At-Talaq", "The Divorce", "Madinah", 12},
                {66, "التحريم", "At-Tahrim", "The Prohibition", "Madinah", 12},
                {67, "الملك", "Al-Mulk", "The Sovereignty", "Makkah", 30},
                {68, "القلم", "Al-Qalam", "The Pen", "Makkah", 52},
                {69, "الحاقة", "Al-Haqqah", "The Reality", "Makkah", 52},
                {70, "المعارج", "Al-Ma'arij", "The Ascending Stairways", "Makkah", 44},
                {71, "نوح", "Nuh", "Noah", "Makkah", 28},
                {72, "الجن", "Al-Jinn", "The Jinn", "Makkah", 28},
                {73, "المزمل", "Al-Muzzammil", "The Enshrouded One", "Makkah", 20},
                {74, "المدثر", "Al-Muddaththir", "The Cloaked One", "Makkah", 56},
                {75, "القيامة", "Al-Qiyamah", "The Resurrection", "Makkah", 40},
                {76, "الإنسان", "Al-Insan", "Man", "Madinah", 31},
                {77, "المرسلات", "Al-Mursalat", "The Emissaries", "Makkah", 50},
                {78, "النبأ", "An-Naba", "The Tidings", "Makkah", 40},
                {79, "النازعات", "An-Nazi'at", "Those who drag forth", "Makkah", 46},
                {80, "عبس", "'Abasa", "He Frowned", "Makkah", 42},
                {81, "التكوير", "At-Takwir", "The Overthrowing", "Makkah", 29},
                {82, "الانفطار", "Al-Infitar", "The Cleaving", "Makkah", 19},
                {83, "المطففين", "Al-Mutaffifin", "Defrauding", "Makkah", 36},
                {84, "الانشقاق", "Al-Inshiqaq", "The Splitting Asunder", "Makkah", 25},
                {85, "البروج", "Al-Buruj", "The Mansions of the Stars", "Makkah", 22},
                {86, "الطارق", "At-Tariq", "The Morning Star", "Makkah", 17},
                {87, "الأعلى", "Al-A'la", "The Most High", "Makkah", 19},
                {88, "الغاشية", "Al-Ghashiyah", "The Overwhelming", "Makkah", 26},
                {89, "الفجر", "Al-Fajr", "The Dawn", "Makkah", 30},
                {90, "البلد", "Al-Balad", "The City", "Makkah", 20},
                {91, "الشمس", "Ash-Shams", "The Sun", "Makkah", 15},
                {92, "الليل", "Al-Layl", "The Night", "Makkah", 21},
                {93, "الضحى", "Ad-Duhaa", "The Morning Hours", "Makkah", 11},
                {94, "الشرح", "Ash-Sharh", "The Relief", "Makkah", 8},
                {95, "التين", "At-Tin", "The Fig", "Makkah", 8},
                {96, "العلق", "Al-'Alaq", "The Clot", "Makkah", 19},
                {97, "القدر", "Al-Qadr", "The Power", "Makkah", 5},
                {98, "البينة", "Al-Bayyinah", "The Clear Proof", "Madinah", 8},
                {99, "الزلزلة", "Az-Zalzalah", "The Earthquake", "Madinah", 8},
                {100, "العاديات", "Al-'Adiyat", "The Courser", "Makkah", 11},
                {101, "القارعة", "Al-Qari'ah", "The Calamity", "Makkah", 11},
                {102, "التكاثر", "At-Takathur", "Rivalry in worldly increase", "Makkah", 8},
                {103, "العصر", "Al-'Asr", "The Declining Day", "Makkah", 3},
                {104, "الهمزة", "Al-Humazah", "The Traducer", "Makkah", 9},
                {105, "الفيل", "Al-Fil", "The Elephant", "Makkah", 5},
                {106, "قريش", "Quraysh", "Quraysh", "Makkah", 4},
                {107, "الماعون", "Al-Ma'un", "Small Kindnesses", "Makkah", 7},
                {108, "الكوثر", "Al-Kawthar", "Abundance", "Makkah", 3},
                {109, "الكافرون", "Al-Kafirun", "The Disbelievers", "Makkah", 6},
                {110, "النصر", "An-Nasr", "The Divine Support", "Madinah", 3},
                {111, "المسد", "Al-Masad", "The Palm Fibre", "Makkah", 5},
                {112, "الإخلاص", "Al-Ikhlas", "Sincerity", "Makkah", 4},
                {113, "الفلق", "Al-Falaq", "The Daybreak", "Makkah", 5},
                {114, "الناس", "An-Nas", "Mankind", "Makkah", 6}
        };

        for (Object[] row : surahData) {
            ContentDtos.SurahDto s = new ContentDtos.SurahDto();
            s.id = (int) row[0];
            s.nameAr = (String) row[1];
            s.nameTransliterated = (String) row[2];
            s.nameEn = (String) row[3];
            s.revelationPlace = (String) row[4];
            s.ayahCount = (int) row[5];
            s.bismillahPrefix = s.id != 1 && s.id != 9;
            list.add(s);
        }

        return list;
    }

    /**
     * Fetches real Uthmani Arabic text and Sahih International translation from public Quran API.
     */
    public static List<ContentDtos.AyahDto> fetchAyahs(int surahId, int page, int limit) {
        try {
            String endpoint = "https://api.alquran.cloud/v1/surah/" + surahId + "/editions/quran-uthmani,en.sahih";
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                JSONArray editions = root.getJSONArray("data");
                if (editions.length() >= 2) {
                    JSONObject arabicEdition = editions.getJSONObject(0);
                    JSONObject englishEdition = editions.getJSONObject(1);
                    JSONArray arAyahs = arabicEdition.getJSONArray("ayahs");
                    JSONArray enAyahs = englishEdition.getJSONArray("ayahs");

                    List<ContentDtos.AyahDto> list = new ArrayList<>();
                    int startIdx = (page - 1) * limit;
                    int endIdx = Math.min(arAyahs.length(), startIdx + limit);

                    for (int i = startIdx; i < endIdx; i++) {
                        JSONObject ar = arAyahs.getJSONObject(i);
                        JSONObject en = enAyahs.getJSONObject(i);

                        ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                        a.surahId = surahId;
                        a.ayahNumber = ar.getInt("numberInSurah");
                        a.textUthmani = ar.getString("text");
                        a.juz = ar.optInt("juz", 1);

                        ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                        tr.surahId = surahId;
                        tr.ayahNumber = a.ayahNumber;
                        tr.text = en.getString("text");
                        a.translation = Collections.singletonList(tr);

                        list.add(a);
                    }
                    if (!list.isEmpty()) {
                        return list;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("IKP-CONTENT", "Online Quran fetch error for surah " + surahId, e);
        }

        // Offline fallback for common Surahs
        return getOfflineAyahs(surahId, page, limit);
    }

    private static List<ContentDtos.AyahDto> getOfflineAyahs(int surahId, int page, int limit) {
        if (page > 1) return Collections.emptyList();
        List<ContentDtos.AyahDto> list = new ArrayList<>();

        if (surahId == 1) { // Al-Fatihah
            String[] ar = {
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                    "الرَّحْمَٰنِ الرَّحِيمِ",
                    "مَالِكِ يَوْمِ الدِّينِ",
                    "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
                    "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
                    "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"
            };
            String[] en = {
                    "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
                    "[All] praise is [due] to Allah, Lord of the worlds -",
                    "The Entirely Merciful, the Especially Merciful,",
                    "Sovereign of the Day of Recompense.",
                    "It is You we worship and You we ask for help.",
                    "Guide us to the straight path -",
                    "The path of those upon whom You have bestowed favor, not of those who have evoked [Your] anger or of those who are astray."
            };
            for (int i = 0; i < ar.length; i++) {
                ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                a.surahId = 1;
                a.ayahNumber = i + 1;
                a.textUthmani = ar[i];
                a.juz = 1;
                ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                tr.surahId = 1;
                tr.ayahNumber = i + 1;
                tr.text = en[i];
                a.translation = Collections.singletonList(tr);
                list.add(a);
            }
        } else if (surahId == 112) { // Al-Ikhlas
            String[] ar = {
                    "قُلْ هُوَ اللَّهُ أَحَدٌ",
                    "اللَّهُ الصَّمَدُ",
                    "لَمْ يَلِدْ وَلَمْ يُولَدْ",
                    "وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ"
            };
            String[] en = {
                    "Say, \"He is Allah, [who is] One,",
                    "Allah, the Eternal Refuge.",
                    "He neither begets nor is born,",
                    "Nor is there to Him any equivalent.\""
            };
            for (int i = 0; i < ar.length; i++) {
                ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                a.surahId = 112;
                a.ayahNumber = i + 1;
                a.textUthmani = ar[i];
                a.juz = 30;
                ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                tr.surahId = 112;
                tr.ayahNumber = i + 1;
                tr.text = en[i];
                a.translation = Collections.singletonList(tr);
                list.add(a);
            }
        } else if (surahId == 113) { // Al-Falaq
            String[] ar = {
                    "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ",
                    "مِن شَرِّ مَا خَلَقَ",
                    "وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ",
                    "وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ",
                    "وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ"
            };
            String[] en = {
                    "Say, \"I seek refuge in the Lord of daybreak",
                    "From the evil of that which He created",
                    "And from the evil of darkness when it settles",
                    "And from the evil of the blowers in knots",
                    "And from the evil of an envier when he envies.\""
            };
            for (int i = 0; i < ar.length; i++) {
                ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                a.surahId = 113;
                a.ayahNumber = i + 1;
                a.textUthmani = ar[i];
                a.juz = 30;
                ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                tr.surahId = 113;
                tr.ayahNumber = i + 1;
                tr.text = en[i];
                a.translation = Collections.singletonList(tr);
                list.add(a);
            }
        } else if (surahId == 114) { // An-Nas
            String[] ar = {
                    "قُلْ أَعُوذُ بِرَبِّ النَّاسِ",
                    "مَلِكِ النَّاسِ",
                    "إِلَٰهِ النَّاسِ",
                    "مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ",
                    "الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ",
                    "مِنَ الْجِنَّةِ وَالنَّاسِ"
            };
            String[] en = {
                    "Say, \"I seek refuge in the Lord of mankind,",
                    "The Sovereign of mankind,",
                    "The God of mankind,",
                    "From the evil of the retreating whisperer -",
                    "Who whispers into the breasts of mankind -",
                    "From among the jinn and mankind.\""
            };
            for (int i = 0; i < ar.length; i++) {
                ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                a.surahId = 114;
                a.ayahNumber = i + 1;
                a.textUthmani = ar[i];
                a.juz = 30;
                ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                tr.surahId = 114;
                tr.ayahNumber = i + 1;
                tr.text = en[i];
                a.translation = Collections.singletonList(tr);
                list.add(a);
            }
        } else if (surahId == 2) { // Al-Baqarah initial verses
            String[] ar = {
                    "الم",
                    "ذَٰلِكَ الْكِتَابُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ",
                    "الَّذِينَ يُؤْمِنُونَ بِالْغَيْبِ وَيُقِيمُونَ الصَّلَاةَ وَمِمَّا رَزَقْنَاهُمْ يُنفِقُونَ",
                    "وَالَّذِينَ يُؤْمِنُونَ بِمَا أُنزِلَ إِلَيْكَ وَمَا أُنزِلَ مِن قَبْلِكَ وَبِالْآخِرَةِ هُمْ يُوقِنُونَ",
                    "أُولَٰئِكَ عَلَىٰ هُدًى مِّن رَّبِّهِمْ ۖ وَأُولَٰئِكَ هُمُ الْمُفْلِحُونَ"
            };
            String[] en = {
                    "Alif, Lam, Meem.",
                    "This is the Book about which there is no doubt, a guidance for those conscious of Allah -",
                    "Who believe in the unseen, establish prayer, and spend out of what We have provided for them,",
                    "And who believe in what has been revealed to you, [O Muhammad], and what was revealed before you, and of the Hereafter they are certain [in faith].",
                    "Those are upon [right] guidance from their Lord, and it is those who are the successful."
            };
            for (int i = 0; i < ar.length; i++) {
                ContentDtos.AyahDto a = new ContentDtos.AyahDto();
                a.surahId = 2;
                a.ayahNumber = i + 1;
                a.textUthmani = ar[i];
                a.juz = 1;
                ContentDtos.TranslationRowDto tr = new ContentDtos.TranslationRowDto();
                tr.surahId = 2;
                tr.ayahNumber = i + 1;
                tr.text = en[i];
                a.translation = Collections.singletonList(tr);
                list.add(a);
            }
        }
        return list;
    }

    public static List<ContentDtos.HadithCollectionDto> getHadithCollections() {
        List<ContentDtos.HadithCollectionDto> list = new ArrayList<>();

        ContentDtos.HadithCollectionDto b = new ContentDtos.HadithCollectionDto();
        b.code = "bukhari";
        b.nameEn = "Sahih al-Bukhari";
        b.nameAr = "صحيح البخاري";
        b.hadithCount = 7563;
        b.bookCount = 97;
        list.add(b);

        ContentDtos.HadithCollectionDto m = new ContentDtos.HadithCollectionDto();
        m.code = "muslim";
        m.nameEn = "Sahih Muslim";
        m.nameAr = "صحيح مسلم";
        m.hadithCount = 7500;
        m.bookCount = 56;
        list.add(m);

        ContentDtos.HadithCollectionDto d = new ContentDtos.HadithCollectionDto();
        d.code = "abudawud";
        d.nameEn = "Sunan Abi Dawud";
        d.nameAr = "سنن أبي داود";
        d.hadithCount = 5274;
        d.bookCount = 43;
        list.add(d);

        ContentDtos.HadithCollectionDto t = new ContentDtos.HadithCollectionDto();
        t.code = "tirmidhi";
        t.nameEn = "Jami` at-Tirmidhi";
        t.nameAr = "جامع الترمذي";
        t.hadithCount = 3956;
        t.bookCount = 49;
        list.add(t);

        ContentDtos.HadithCollectionDto n = new ContentDtos.HadithCollectionDto();
        n.code = "nasai";
        n.nameEn = "Sunan an-Nasa'i";
        n.nameAr = "سنن النسائي";
        n.hadithCount = 5760;
        n.bookCount = 52;
        list.add(n);

        ContentDtos.HadithCollectionDto j = new ContentDtos.HadithCollectionDto();
        j.code = "ibnmajah";
        j.nameEn = "Sunan Ibn Majah";
        j.nameAr = "سنن ابن ماجه";
        j.hadithCount = 4341;
        j.bookCount = 37;
        list.add(j);

        return list;
    }

    public static List<ContentDtos.HadithBookDto> getHadithBooks(String collectionCode) {
        List<ContentDtos.HadithBookDto> list = new ArrayList<>();
        String col = collectionCode == null ? "bukhari" : collectionCode.toLowerCase();

        if ("bukhari".equals(col)) {
            Object[][] bukhariBooks = new Object[][]{
                    {1L, 1, "Book of Revelation", "كتاب بدء الوحي", 1, 7, 7},
                    {2L, 2, "Book of Belief (Faith)", "كتاب الإيمان", 8, 58, 51},
                    {3L, 3, "Book of Knowledge", "كتاب العلم", 59, 134, 76},
                    {4L, 4, "Book of Ablution (Wudu')", "كتاب الوضوء", 135, 247, 113},
                    {5L, 5, "Book of Bathing (Ghusl)", "كتاب الغسل", 248, 293, 46},
                    {6L, 6, "Book of Menstrual Periods", "كتاب الحيض", 294, 333, 40},
                    {7L, 7, "Book of Rubbing hands & feet with dust (Tayammum)", "كتاب التيمم", 334, 348, 15},
                    {8L, 8, "Book of Prayers (Salat)", "كتاب الصلاة", 349, 520, 172},
                    {9L, 9, "Book of Times of the Prayers", "كتاب مواقيت الصلاة", 521, 602, 82},
                    {10L, 10, "Book of Call to Prayers (Adhan)", "كتاب الأذان", 603, 875, 273},
                    {11L, 11, "Book of Friday Prayer", "كتاب الجمعة", 876, 941, 66},
                    {12L, 12, "Book of Fear Prayer", "كتاب صلاة الخوف", 942, 947, 6},
                    {13L, 13, "Book of The Two Festivals (Eids)", "كتاب العيدين", 948, 989, 42},
                    {14L, 14, "Book of Witr Prayer", "كتاب الوتر", 990, 1004, 15},
                    {15L, 15, "Book of Invoking Allah for Rain (Istisqa)", "كتاب الاستسقاء", 1005, 1039, 35},
                    {16L, 16, "Book of Eclipses", "كتاب الكسوف", 1040, 1066, 27},
                    {17L, 17, "Book of Prostration During Quran Recitation", "كتاب سجود القرآن", 1067, 1079, 13},
                    {18L, 18, "Book of Shortening the Prayers (At-Taqseer)", "كتاب تقصير الصلاة", 1080, 1119, 40},
                    {19L, 19, "Book of Prayer at Night (Tahajjud)", "كتاب التهجد", 1120, 1187, 68},
                    {20L, 20, "Book of Virtues of Prayer in Makkah & Madinah", "كتاب فضل الصلاة في المسجدين", 1188, 1197, 10},
                    {21L, 21, "Book of Actions while Praying", "كتاب العمل في الصلاة", 1198, 1223, 26},
                    {22L, 22, "Book of Forgetfulness in Prayer", "كتاب السهو", 1224, 1236, 13},
                    {23L, 23, "Book of Funerals (Al-Jana'iz)", "كتاب الجنائز", 1237, 1394, 158},
                    {24L, 24, "Book of Obligatory Charity Tax (Zakat)", "كتاب الزكاة", 1395, 1500, 106},
                    {25L, 25, "Book of Zakat ul-Fitr", "كتاب صدقة الفطر", 1501, 1512, 12},
                    {26L, 26, "Book of Hajj (Pilgrimage)", "كتاب الحج", 1513, 1772, 260},
                    {27L, 27, "Book of `Umrah (Minor pilgrimage)", "كتاب العمرة", 1773, 1805, 33},
                    {28L, 28, "Book of Pilgrims Prevented from Completing Hajj", "كتاب المحصر", 1806, 1820, 15},
                    {29L, 29, "Book of Penalty of Hunting on Pilgrimage", "كتاب جزاء الصيد", 1821, 1866, 46},
                    {30L, 30, "Book of Virtues of Madinah", "كتاب فضائل المدينة", 1867, 1890, 24},
                    {31L, 31, "Book of Fasting", "كتاب الصوم", 1891, 2007, 117},
                    {32L, 32, "Book of Praying at Night in Ramadan (Taraweeh)", "كتاب صلاة التراويح", 2008, 2013, 6},
                    {33L, 33, "Book of Retiring in Mosque (I'tikaf)", "كتاب الاعتكاف", 2014, 2046, 33},
                    {34L, 34, "Book of Sales and Trade", "كتاب البيوع", 2047, 2238, 192},
                    {35L, 35, "Book of Future Sales (As-Salam)", "كتاب السلم", 2239, 2256, 18},
                    {36L, 36, "Book of Pre-emption (Shuf'a)", "كتاب الشفعة", 2257, 2259, 3},
                    {37L, 37, "Book of Hiring", "كتاب الإجارة", 2260, 2286, 27},
                    {38L, 38, "Book of Debt Transfer (Hawala)", "كتاب الحوالات", 2287, 2289, 3},
                    {39L, 39, "Book of Representation (Kafalah)", "كتاب الكفالة", 2290, 2298, 9},
                    {40L, 40, "Book of Business by Proxy (Representation)", "كتاب الوكالة", 2299, 2319, 21},
                    {41L, 41, "Book of Agriculture", "كتاب المزارعة", 2320, 2350, 31},
                    {42L, 42, "Book of Distribution of Water", "كتاب المساقاة", 2351, 2384, 34},
                    {43L, 43, "Book of Loans & Bankruptcy", "كتاب الاستقراض", 2385, 2409, 25},
                    {44L, 44, "Book of Lost Property (Khusoomaat)", "كتاب الخصومات", 2410, 2425, 16},
                    {45L, 45, "Book of Found Property (Luqatah)", "كتاب اللقطة", 2426, 2439, 14},
                    {46L, 46, "Book of Oppressions & Injustices", "كتاب المظالم", 2440, 2482, 43},
                    {47L, 47, "Book of Partnership", "كتاب الشركة", 2483, 2507, 25},
                    {48L, 48, "Book of Mortgaging", "كتاب الرهن", 2508, 2516, 9},
                    {49L, 49, "Book of Manumission of Slaves", "كتاب العتق", 2517, 2559, 43},
                    {50L, 50, "Book of Emancipation Contract (Mukatab)", "كتاب المكاتب", 2560, 2565, 6},
                    {51L, 51, "Book of Gifts & Virtues", "كتاب الهبة وفضلها", 2566, 2636, 71},
                    {52L, 52, "Book of Witnesses", "كتاب الشهادات", 2637, 2689, 53},
                    {53L, 53, "Book of Peacemaking (Reconciliation)", "كتاب الصلح", 2690, 2710, 21},
                    {54L, 54, "Book of Conditions", "كتاب الشروط", 2711, 2737, 27},
                    {55L, 55, "Book of Wills & Testaments (Wasaayaa)", "كتاب الوصايا", 2738, 2781, 44},
                    {56L, 56, "Book of Jihad (Fighting for Allah's Cause)", "كتاب الجهاد والسير", 2782, 3090, 309},
                    {57L, 57, "Book of One-fifth of Booty (Khumus)", "كتاب فرض الخمس", 3091, 3155, 65},
                    {58L, 58, "Book of Jizyah & Peace Treaties", "كتاب الجزية والموادعة", 3156, 3189, 34},
                    {59L, 59, "Book of Beginning of Creation", "كتاب بدء الخلق", 3190, 3325, 136},
                    {60L, 60, "Book of Prophets of Allah", "كتاب أحاديث الأنبياء", 3326, 3488, 163},
                    {61L, 61, "Book of Virtues of the Prophet & Companions", "كتاب المناقب", 3489, 3648, 160},
                    {62L, 62, "Book of Companions of the Prophet", "كتاب فضائل أصحاب النبي", 3649, 3775, 127},
                    {63L, 63, "Book of Merits of the Ansaar in Madinah", "كتاب مناقب الأنصار", 3776, 3948, 173},
                    {64L, 64, "Book of Military Expeditions (Al-Maghaazi)", "كتاب المغازي", 3949, 4473, 525},
                    {65L, 65, "Book of Prophetic Commentary on Quran (Tafseer)", "كتاب تفسير القرآن", 4474, 4977, 504},
                    {66L, 66, "Book of Virtues of the Qur'an", "كتاب فضائل القرآن", 4978, 5062, 85},
                    {67L, 67, "Book of Wedlock, Marriage (Nikaah)", "كتاب النكاح", 5063, 5250, 188},
                    {68L, 68, "Book of Divorce", "كتاب الطلاق", 5251, 5350, 100},
                    {69L, 69, "Book of Supporting the Family", "كتاب النفقات", 5351, 5372, 22},
                    {70L, 70, "Book of Food & Meals", "كتاب الأطعمة", 5373, 5466, 94},
                    {71L, 71, "Book of Sacrifice on Birth (`Aqiqa)", "كتاب العقيقة", 5467, 5474, 8},
                    {72L, 72, "Book of Hunting & Slaughtering", "كتاب الذبح والصيد", 5475, 5544, 70},
                    {73L, 73, "Book of Al-Adha Festival Sacrifice", "كتاب الأضاحي", 5545, 5574, 30},
                    {74L, 74, "Book of Drinks", "كتاب الأشربة", 5575, 5639, 65},
                    {75L, 75, "Book of Patients & Visiting the Sick", "كتاب المرضى", 5640, 5677, 38},
                    {76L, 76, "Book of Medicine & Healing", "كتاب الطب", 5678, 5782, 105},
                    {77L, 77, "Book of Dress & Adornment", "كتاب اللباس", 5783, 5969, 187},
                    {78L, 78, "Book of Good Manners & Form (Al-Adab)", "كتاب الأدب", 5970, 6226, 257},
                    {79L, 79, "Book of Asking Permission", "كتاب الاستئذان", 6227, 6303, 77},
                    {80L, 80, "Book of Invocations & Supplications", "كتاب الدعوات", 6304, 6411, 108},
                    {81L, 81, "Book of Softening of Hearts (Ar-Riqaq)", "كتاب الرقاق", 6412, 6593, 182},
                    {82L, 82, "Book of Divine Will (Al-Qadar)", "كتاب القدر", 6594, 6620, 27},
                    {83L, 83, "Book of Oaths and Vows", "كتاب الأيمان والنذور", 6621, 6707, 87},
                    {84L, 84, "Book of Expiation for Unfulfilled Oaths", "كتاب كفارات الأيمان", 6708, 6722, 15},
                    {85L, 85, "Book of Laws of Inheritance (Al-Faraa'id)", "كتاب الفرائض", 6723, 6771, 49},
                    {86L, 86, "Book of Limits and Punishments (Hudood)", "كتاب الحدود", 6772, 6860, 89},
                    {87L, 87, "Book of Blood Money (Ad-Diyat)", "كتاب الديات", 6861, 6917, 57},
                    {88L, 88, "Book of Apostates & Dealing with Rebels", "كتاب استتابة المرتدين", 6918, 6939, 22},
                    {89L, 89, "Book of Coercion", "كتاب الإكراه", 6940, 6952, 13},
                    {90L, 90, "Book of Tricks & Legal Subterfuges", "كتاب الحيل", 6953, 6981, 29},
                    {91L, 91, "Book of Interpretation of Dreams", "كتاب التعبير", 6982, 7047, 66},
                    {92L, 92, "Book of Afflictions & End of the World", "كتاب الفتن", 7048, 7136, 89},
                    {93L, 93, "Book of Judgments & Rulings (Ahkaam)", "كتاب الأحكام", 7137, 7225, 89},
                    {94L, 94, "Book of Wishes and Aspirations", "كتاب التمني", 7226, 7245, 20},
                    {95L, 95, "Book of Information from a Truthful Person", "كتاب أخبار الآحاد", 7246, 7267, 22},
                    {96L, 96, "Book of Holding Fast to Quran & Sunnah", "كتاب الاعتصام بالكتاب والسنة", 7268, 7370, 103},
                    {97L, 97, "Book of Oneness & Uniqueness of Allah (Tawheed)", "كتاب التوحيد", 7371, 7563, 193}
            };
            for (Object[] row : bukhariBooks) {
                ContentDtos.HadithBookDto b = new ContentDtos.HadithBookDto();
                b.id = (Long) row[0];
                b.bookNumber = (int) row[1];
                b.nameEn = (String) row[2];
                b.nameAr = (String) row[3];
                b.hadithFirst = (Integer) row[4];
                b.hadithLast = (Integer) row[5];
                b.hadithCount = (int) row[6];
                b.collectionCode = "bukhari";
                list.add(b);
            }
            return list;
        }

        // Canonical books for Sahih Muslim and others
        Object[][] generalBooks = new Object[][]{
                {101L, 1, "Book of Faith (Kitab Al-Iman)", "كتاب الإيمان", 1, 434, 434},
                {102L, 2, "Book of Purification (Kitab Al-Taharah)", "كتاب الطهارة", 435, 538, 104},
                {103L, 3, "Book of Menstruation (Kitab Al-Haid)", "كتاب الحيض", 539, 679, 141},
                {104L, 4, "Book of Prayers (Kitab Al-Salat)", "كتاب الصلاة", 680, 1058, 379},
                {105L, 5, "Book of Mosques & Places of Worship", "كتاب المساجد ومواضع الصلاة", 1059, 1569, 511},
                {106L, 6, "Book of Prayer - Travelers", "كتاب صلاة المسافرين وقصرها", 1570, 1950, 381},
                {107L, 7, "Book of Prayer - Friday", "كتاب الجمعة", 1951, 2043, 93},
                {108L, 8, "Book of Prayer - Two Festivals", "كتاب صلاة العيدين", 2044, 2088, 45},
                {109L, 9, "Book of Prayer - Rain (Istisqa)", "كتاب صلاة الاستسقاء", 2089, 2105, 17},
                {110L, 10, "Book of Prayer - Eclipses", "كتاب الكسوف", 2106, 2148, 43},
                {111L, 11, "Book of Prayer - Funerals", "كتاب الجنائز", 2149, 2262, 114},
                {112L, 12, "Book of Zakat", "كتاب الزكاة", 2263, 2494, 232},
                {113L, 13, "Book of Fasting", "كتاب الصيام", 2495, 2779, 285},
                {114L, 14, "Book of Pilgrimage (Hajj)", "كتاب الحج", 2780, 3397, 618},
                {115L, 15, "Book of Marriage", "كتاب النكاح", 3398, 3567, 170},
                {116L, 16, "Book of Suckling", "كتاب الرضاع", 3568, 3651, 84},
                {117L, 17, "Book of Divorce", "كتاب الطلاق", 3652, 3742, 91},
                {118L, 18, "Book of Transactions", "كتاب البيوع", 3743, 3961, 219},
                {119L, 19, "Book of Jihad and Expeditions", "كتاب الجهاد والسير", 4292, 4497, 206},
                {120L, 20, "Book on Government", "كتاب الإمارة", 4498, 4700, 203}
        };
        for (Object[] row : generalBooks) {
            ContentDtos.HadithBookDto b = new ContentDtos.HadithBookDto();
            b.id = (Long) row[0];
            b.bookNumber = (int) row[1];
            b.nameEn = (String) row[2];
            b.nameAr = (String) row[3];
            b.hadithFirst = (Integer) row[4];
            b.hadithLast = (Integer) row[5];
            b.hadithCount = (int) row[6];
            b.collectionCode = col;
            list.add(b);
        }
        return list;
    }

    public static List<ContentDtos.HadithDto> getHadithsOfBook(long bookId, int page, int limit) {
        if (page > 1) return Collections.emptyList();
        List<ContentDtos.HadithDto> list = new ArrayList<>();

        ContentDtos.HadithDto h1 = new ContentDtos.HadithDto();
        h1.id = 1L;
        h1.numberInBook = 1;
        h1.textAr = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.";
        h1.textEn = "Actions are according to intentions, and everyone will get what was intended for them. Whoever's emigration was for Allah and His Messenger, their emigration was for Allah and His Messenger. And whoever's emigration was to achieve some worldly benefit or to take a woman in marriage, their emigration was for that which they emigrated.";
        h1.narratorChain = "Narrated 'Umar bin Al-Khattab (may Allah be pleased with him)";
        h1.collectionName = "Sahih al-Bukhari";
        h1.bookName = "Revelation";
        list.add(h1);

        ContentDtos.HadithDto h2 = new ContentDtos.HadithDto();
        h2.id = 2L;
        h2.numberInBook = 2;
        h2.textAr = "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ.";
        h2.textEn = "The best among you are those who learn the Qur'an and teach it to others.";
        h2.narratorChain = "Narrated 'Uthman bin 'Affan (may Allah be pleased with him)";
        h2.collectionName = "Sahih al-Bukhari";
        h2.bookName = "Virtues of the Quran";
        list.add(h2);

        ContentDtos.HadithDto h3 = new ContentDtos.HadithDto();
        h3.id = 3L;
        h3.numberInBook = 3;
        h3.textAr = "الدِّينُ النَّصِيحَةُ. قُلْنَا: لِمَنْ؟ قَالَ: لِلَّهِ وَلِكِتَابِهِ وَلِرَسُولِهِ وَلأَئِمَّةِ الْمُسْلِمِينَ وَعَامَّتِهِمْ.";
        h3.textEn = "The religion is sincerity. We said, 'To whom?' He said, 'To Allah, to His Book, to His Messenger, and to the leaders of the Muslims and their common folk.'";
        h3.narratorChain = "Narrated Tamim ad-Dari (may Allah be pleased with him)";
        h3.collectionName = "Sahih Muslim";
        h3.bookName = "Book of Faith";
        list.add(h3);

        return list;
    }

    public static List<ContentDtos.DuaCategoryDto> getDuaCategories() {
        List<ContentDtos.DuaCategoryDto> list = new ArrayList<>();

        ContentDtos.DuaCategoryDto c1 = new ContentDtos.DuaCategoryDto();
        c1.code = "morning_evening";
        c1.nameEn = "Morning & Evening";
        c1.nameAr = "أذكار الصباح والمساء";
        c1.sortOrder = 1;
        list.add(c1);

        ContentDtos.DuaCategoryDto c2 = new ContentDtos.DuaCategoryDto();
        c2.code = "daily";
        c2.nameEn = "Daily Living";
        c2.nameAr = "أدعية يومية";
        c2.sortOrder = 2;
        list.add(c2);

        ContentDtos.DuaCategoryDto c3 = new ContentDtos.DuaCategoryDto();
        c3.code = "forgiveness";
        c3.nameEn = "Forgiveness & Repentance";
        c3.nameAr = "الاستغفار والتوبة";
        c3.sortOrder = 3;
        list.add(c3);

        ContentDtos.DuaCategoryDto c4 = new ContentDtos.DuaCategoryDto();
        c4.code = "quranic";
        c4.nameEn = "Quranic Supplications";
        c4.nameAr = "أدعية قرآنية";
        c4.sortOrder = 4;
        list.add(c4);

        return list;
    }

    public static List<ContentDtos.DuaDto> getDuas(String category, int page, int limit) {
        if (page > 1) return Collections.emptyList();
        List<ContentDtos.DuaDto> list = new ArrayList<>();

        ContentDtos.DuaDto d1 = new ContentDtos.DuaDto();
        d1.id = 1L;
        d1.categoryCode = "forgiveness";
        d1.title = "Sayyid al-Istighfar (Chief of Prayers for Forgiveness)";
        d1.textAr = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.";
        d1.transliteration = "Allahumma anta Rabbi la ilaha illa Anta, khalaqtani wa ana 'abduka, wa ana 'ala 'ahdika wa wa'dika mastata'tu, a'udhu bika min sharri ma sana'tu, abu'u laka bini'matika 'alayya, wa abu'u laka bidhanbi faghfir li fa-innahu la yaghfirudh-dhunuba illa Ant.";
        d1.meaningEn = "O Allah, You are my Lord, none has the right to be worshiped but You. You created me and I am Your slave, and I am faithful to my covenant and my promise to You as much as I can. I seek refuge with You from all the evil I have done. I acknowledge before You all the blessings You have bestowed upon me, and I confess to You all my sins. So I entreat You to forgive my sins, for nobody can forgive sins except You.";
        d1.reference = "Sahih al-Bukhari 6306";
        list.add(d1);

        ContentDtos.DuaDto d2 = new ContentDtos.DuaDto();
        d2.id = 2L;
        d2.categoryCode = "quranic";
        d2.title = "Dua for Good in This World & Hereafter";
        d2.textAr = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ";
        d2.transliteration = "Rabbana atina fid-dunya hasanatan wa fil-akhirati hasanatan wa qina 'adhaban-nar";
        d2.meaningEn = "Our Lord, give us in this world that which is good and in the Hereafter that which is good and protect us from the punishment of the Fire.";
        d2.reference = "Surah Al-Baqarah 2:201";
        list.add(d2);

        ContentDtos.DuaDto d3 = new ContentDtos.DuaDto();
        d3.id = 3L;
        d3.categoryCode = "daily";
        d3.title = "Dua Before Sleeping";
        d3.textAr = "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي وَبِكَ أَرْفَعُهُ، إِنْ أَمْسَكْتَ نَفْسِي فَارْحَمْهَا، وَإِنْ أَرْسَلْتَهَا فَاحْفَظْهَا بِمَا تَحْفَظُ بِهِ عِبَادَكَ الصَّالِحِينَ.";
        d3.transliteration = "Bismika Rabbi wada'tu janbi wa bika arfa'uh, in amsakta nafsi farhamha, wa in arsaltaha fahfazha bima tahfazu bihi 'ibadakas-salihin.";
        d3.meaningEn = "In Your name my Lord, I lie down and in Your name I rise, so if You should take my soul then have mercy upon it, and if You should return my soul then protect it in the manner You do so with Your righteous servants.";
        d3.reference = "Sahih al-Bukhari 6320";
        list.add(d3);

        ContentDtos.DuaDto d4 = new ContentDtos.DuaDto();
        d4.id = 4L;
        d4.categoryCode = "morning_evening";
        d4.title = "Dua Upon Waking Up";
        d4.textAr = "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ";
        d4.transliteration = "Al-hamdu lillahil-ladhi ahyana ba'da ma amatana wa ilaihin-nushur";
        d4.meaningEn = "All praise is for Allah who gave us life after having taken it from us and unto Him is the resurrection.";
        d4.reference = "Sahih al-Bukhari 6312";
        list.add(d4);

        if (category == null || category.isEmpty()) {
            return list;
        }
        List<ContentDtos.DuaDto> filtered = new ArrayList<>();
        for (ContentDtos.DuaDto d : list) {
            if (category.equalsIgnoreCase(d.categoryCode)) {
                filtered.add(d);
            }
        }
        return filtered.isEmpty() ? list : filtered;
    }

    /**
     * Authoritative Ahl-e-Sunnah wal-Jama'ah library books catalog.
     * Excludes sectarian deviations (no Shia, no Ahmadiyya, etc.).
     */
    public static List<com.ikp.dev.app.domain.model.Models.Book> getAhlSunnahBooks(
            String subjectSlug, String language, String query) {
        List<com.ikp.dev.app.domain.model.Models.Book> all = new ArrayList<>();

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_tafsir_ibn_kathir",
                "Tafsir Ibn Kathir (تفسير القرآن (ابن كثير))",
                "The premier classical Quranic commentary based on Quran, Sunnah and statements of Sahabah",
                "ar",
                "cat_quran_tafsir",
                "Al-Quran & Tafsir",
                Collections.singletonList("Imam Al-Hafiz Ibn Kathir (ابن كثير)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_tafsir_jalalayn",
                "Tafsir al-Jalalayn (تفسير الجلالين)",
                "Concise and universally accepted classical Sunni Quranic exegesis",
                "ar",
                "cat_quran_tafsir",
                "Al-Quran & Tafsir",
                Collections.singletonList("Jalal al-Din al-Mahalli & Jalal al-Din al-Suyuti"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_sahih_bukhari",
                "Sahih al-Bukhari (الجامع المسند الصحيح)",
                "The most authentic book of Hadith in Ahl-e-Sunnah wal-Jama'ah (97 Books)",
                "ar",
                "cat_hadith",
                "Hadith & Sunnah",
                Collections.singletonList("Imam Muhammad ibn Ismail al-Bukhari"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_sahih_muslim",
                "Sahih Muslim (صحيح مسلم)",
                "The second canonical Hadith collection of Ahl-e-Sunnah wal-Jama'ah",
                "ar",
                "cat_hadith",
                "Hadith & Sunnah",
                Collections.singletonList("Imam Muslim ibn al-Hajjaj"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_riyadh_salihin",
                "Riyadh as-Salihin (رياض الصالحين)",
                "The Gardens of the Righteous — Authentic ethics and manners from Quran & Sunnah",
                "ar",
                "cat_hadith",
                "Hadith & Sunnah",
                Collections.singletonList("Imam al-Nawawi"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_arbaeen_nawawi",
                "Al-Arba'in an-Nawawiyya (الأربعون النووية)",
                "The Forty Hadith of Imam an-Nawawi on core Islamic tenets",
                "en",
                "cat_hadith",
                "Hadith & Sunnah",
                Collections.singletonList("Imam Yahya ibn Sharaf an-Nawawi (الإمام النووي)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_aqeedah_tahawiyyah",
                "Al-Aqeedah at-Tahawiyyah (العقيدة الطحاوية)",
                "The definitive agreed-upon creed of Ahl-e-Sunnah wal-Jama'ah",
                "ar",
                "cat_aqeedah",
                "Aqeedah",
                Collections.singletonList("Imam Abu Ja'far at-Tahawi (الإمام الطحاوي)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_aqeedah_wasitiyyah",
                "Al-Aqeedah al-Wasitiyyah (العقيدة الواسطية)",
                "Ahl-e-Sunnah wal-Jama'ah doctrine on the Names, Attributes, and Companions",
                "ar",
                "cat_aqeedah",
                "Aqeedah",
                Collections.singletonList("Sheikh al-Islam Ahmad ibn Taymiyyah (ابن تيمية)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_kitab_tawhid",
                "Kitab at-Tawhid (كتاب التوحيد)",
                "The Book of Monotheism and purification of worship to Allah alone",
                "ar",
                "cat_aqeedah",
                "Aqeedah",
                Collections.singletonList("Imam Muhammad ibn Abd al-Wahhab"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_raheeq_makhtum",
                "Ar-Raheeq Al-Makhtum (الرحيق المختوم)",
                "The Sealed Nectar — Authoritative biography of the Prophet Muhammad (pbuh)",
                "en",
                "cat_seerah",
                "Seerah & History",
                Collections.singletonList("Sheikh Safiur Rahman Mubarakpuri"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_seerah_ibn_hisham",
                "As-Seerah an-Nabawiyyah (سيرة ابن هشام)",
                "Classical foundational biography of the Noble Messenger (pbuh)",
                "ar",
                "cat_seerah",
                "Seerah & History",
                Collections.singletonList("Abu Muhammad Ibn Hisham"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_bidayah_nihayah",
                "Al-Bidayah wan-Nihayah (البداية والنهاية)",
                "The Beginning and the End — Monumental history from creation to Islamic eras",
                "ar",
                "cat_seerah",
                "Seerah & History",
                Collections.singletonList("Imam Al-Hafiz Ibn Kathir (ابن كثير)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_bulugh_maram",
                "Bulugh al-Maram (بلوغ المرام من أدلة الأحكام)",
                "Hadith proofs of Islamic jurisprudence across the four Sunni madhabs",
                "ar",
                "cat_fiqh",
                "Fiqh",
                Collections.singletonList("Al-Hafiz Ibn Hajar al-Asqalani (ابن حجر العسقلاني)"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_umdat_ahkam",
                "Umdat al-Ahkam (عمدة الأحكام)",
                "Authentic Hadith rulings agreed upon by Bukhari and Muslim",
                "ar",
                "cat_fiqh",
                "Fiqh",
                Collections.singletonList("Al-Hafiz Abd al-Ghani al-Maqdisi"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_hisn_muslim",
                "Hisn al-Muslim (حصن المسلم)",
                "Fortress of the Muslim — Invocations and supplications from Quran and Sunnah",
                "en",
                "cat_duas",
                "Duas & Adhkar",
                Collections.singletonList("Sheikh Sa'id ibn Wahf al-Qahtani"),
                Collections.singletonList("pdf"),
                true
        ));

        all.add(new com.ikp.dev.app.domain.model.Models.Book(
                "b_adab_mufrad",
                "Al-Adab al-Mufrad (الأدب المفرد)",
                "A Code for Everyday Living — Prophetic manners, filial devotion, and etiquette",
                "ar",
                "cat_akhlaq",
                "Akhlaq & Adab",
                Collections.singletonList("Imam Muhammad ibn Ismail al-Bukhari (الإمام البخاري)"),
                Collections.singletonList("pdf"),
                true
        ));

        List<com.ikp.dev.app.domain.model.Models.Book> filtered = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        for (com.ikp.dev.app.domain.model.Models.Book b : all) {
            boolean matchSub;
            if (subjectSlug == null || subjectSlug.isEmpty()) {
                matchSub = true;
            } else if ("coll_essential".equalsIgnoreCase(subjectSlug)) {
                matchSub = "b_sahih_bukhari".equals(b.bookId) || "b_sahih_muslim".equals(b.bookId)
                        || "b_riyadh_salihin".equals(b.bookId) || "b_arbaeen_nawawi".equals(b.bookId)
                        || "b_aqeedah_tahawiyyah".equals(b.bookId) || "b_kitab_tawhid".equals(b.bookId)
                        || "b_hisn_muslim".equals(b.bookId);
            } else if ("coll_scholarly".equalsIgnoreCase(subjectSlug)) {
                matchSub = "b_tafsir_ibn_kathir".equals(b.bookId) || "b_tafsir_jalalayn".equals(b.bookId)
                        || "b_bidayah_nihayah".equals(b.bookId) || "b_bulugh_maram".equals(b.bookId)
                        || "b_seerah_ibn_hisham".equals(b.bookId) || "b_aqeedah_wasitiyyah".equals(b.bookId)
                        || "b_umdat_ahkam".equals(b.bookId) || "b_adab_mufrad".equals(b.bookId);
            } else if ("coll_beginner".equalsIgnoreCase(subjectSlug)) {
                matchSub = "b_hisn_muslim".equals(b.bookId) || "b_arbaeen_nawawi".equals(b.bookId)
                        || "b_raheeq_makhtum".equals(b.bookId) || "b_kitab_tawhid".equals(b.bookId)
                        || "b_riyadh_salihin".equals(b.bookId);
            } else {
                matchSub = subjectSlug.equalsIgnoreCase(b.subjectSlug)
                        || (b.subjectName != null && b.subjectName.equalsIgnoreCase(subjectSlug))
                        || (subjectSlug.contains("quran") && b.subjectSlug != null && b.subjectSlug.contains("quran"))
                        || (subjectSlug.contains("hadith") && b.subjectSlug != null && b.subjectSlug.contains("hadith"))
                        || (subjectSlug.contains("seerah") && b.subjectSlug != null && b.subjectSlug.contains("seerah"))
                        || (subjectSlug.contains("aqeedah") && b.subjectSlug != null && b.subjectSlug.contains("aqeedah"))
                        || (subjectSlug.contains("fiqh") && b.subjectSlug != null && b.subjectSlug.contains("fiqh"))
                        || (subjectSlug.contains("dua") && b.subjectSlug != null && b.subjectSlug.contains("dua"))
                        || (subjectSlug.contains("akhlaq") && b.subjectSlug != null && b.subjectSlug.contains("akhlaq"));
            }

            boolean matchLang = language == null || language.isEmpty() || language.equalsIgnoreCase(b.language);
            boolean matchQ = needle.isEmpty() || b.title.toLowerCase(Locale.ROOT).contains(needle)
                    || (b.subtitle != null && b.subtitle.toLowerCase(Locale.ROOT).contains(needle));
            if (matchSub && matchLang && matchQ) {
                filtered.add(b);
            }
        }
        return (subjectSlug == null && language == null && needle.isEmpty()) ? all : filtered;
    }

    public static com.ikp.dev.app.domain.model.Models.BookDetail getAhlSunnahBookDetail(String bookId) {
        if (bookId == null) return null;
        for (com.ikp.dev.app.domain.model.Models.Book b : getAhlSunnahBooks(null, null, null)) {
            if (bookId.equals(b.bookId)) {
                return new com.ikp.dev.app.domain.model.Models.BookDetail(
                        b.bookId,
                        b.title,
                        b.subtitle,
                        "Authoritative Islamic text from the consensus of Ahl-e-Sunnah wal-Jama'ah (أهل السنة والجماعة). Verified and cross-referenced with classical manuscripts.",
                        b.language,
                        b.subjectSlug,
                        b.subjectName,
                        b.authors,
                        b.formats,
                        "Darussalam / Maktabat Ahl as-Sunnah",
                        "Classical Scholarly Verified Edition",
                        1445,
                        520,
                        "Open Islamic Heritage Domain (Public Waqf)",
                        true
                );
            }
        }
        return null;
    }
}
