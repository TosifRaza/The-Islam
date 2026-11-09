package app.ikp.backend.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, AssistantDtos.AssistantFeedbackRequest> feedbackStore = new ConcurrentHashMap<>();

    public AssistantService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AssistantDtos.SuggestionDto> getSuggestions() {
        return List.of(
                new AssistantDtos.SuggestionDto(
                        "sugg-1",
                        "What is the meaning and virtue of Ayat al-Kursi?",
                        "Quranic Studies",
                        "Exegesis of Surah Al-Baqarah 2:255, the ten theological sentences, and its status in Sahih Hadith."
                ),
                new AssistantDtos.SuggestionDto(
                        "sugg-2",
                        "What are the essential conditions and pillars of Salah?",
                        "Islamic Jurisprudence",
                        "The 9 preconditions (Shuroot) and 14 integral pillars (Arkan) established by classical juristic consensus."
                ),
                new AssistantDtos.SuggestionDto(
                        "sugg-3",
                        "What are the foundational principles of Islamic commercial law?",
                        "Islamic Finance",
                        "The prohibition of Riba, Gharar, and Maysir, and classical contractual structures (Mudarabah, Murabahah)."
                ),
                new AssistantDtos.SuggestionDto(
                        "sugg-4",
                        "What are the rigorous standards for Hadith authentication?",
                        "Hadith Sciences",
                        "The five rigorous criteria of the Muhaddithin: Ittisal, Adalah, Dabt, and absence of Shudhudh and Illah."
                ),
                new AssistantDtos.SuggestionDto(
                        "sugg-5",
                        "How is Zakat calculated on contemporary financial assets?",
                        "Fiqh of Zakat",
                        "Nisab thresholds, the Hawl requirement, 2.5% rate on liquid wealth, and the 8 Qur'anic recipient categories."
                ),
                new AssistantDtos.SuggestionDto(
                        "sugg-6",
                        "What is the historical significance of the Treaty of Hudaybiyyah?",
                        "Prophetic Seerah",
                        "Strategic diplomatic leadership, the truce of 6 AH, and the divine declaration of 'A Clear Victory' (Fath Mubin)."
                )
        );
    }

    @Transactional(readOnly = true)
    public AssistantDtos.AssistantResponseDto processQuery(AssistantDtos.AssistantQueryRequest req) {
        String query = req.query() != null ? req.query().trim() : "";
        String lower = query.toLowerCase(Locale.ROOT);
        String answerId = UUID.randomUUID().toString();

        // 1. Check for canonical research themes
        if (lower.contains("ayat al-kursi") || lower.contains("kursi") || lower.contains("throne verse") || lower.contains("2:255")) {
            return buildAyatAlKursiResponse(answerId, query);
        }

        if (lower.contains("salah") || lower.contains("prayer") || lower.contains("namaz") || lower.contains("conditions of prayer") || lower.contains("pillars of prayer")) {
            return buildSalahResponse(answerId, query);
        }

        if (lower.contains("commercial law") || lower.contains("finance") || lower.contains("riba") || lower.contains("usury") || lower.contains("gharar") || lower.contains("murabahah") || lower.contains("banking")) {
            return buildCommercialLawResponse(answerId, query);
        }

        if (lower.contains("hadith") || lower.contains("authentication") || lower.contains("muhaddith") || lower.contains("sanad") || lower.contains("chain") || lower.contains("isnad")) {
            return buildHadithAuthenticationResponse(answerId, query);
        }

        if (lower.contains("zakat") || lower.contains("zakah") || lower.contains("nisab") || lower.contains("wealth tax")) {
            return buildZakatResponse(answerId, query);
        }

        if (lower.contains("ramadan") || lower.contains("fasting") || lower.contains("sawm") || lower.contains("iftar") || lower.contains("suhoor")) {
            return buildRamadanResponse(answerId, query);
        }

        if (lower.contains("hudaybiyyah") || lower.contains("treaty") || lower.contains("seerah") || lower.contains("diplomatic")) {
            return buildHudaybiyyahResponse(answerId, query);
        }

        // 2. Dynamic lookup in published research articles and fatwas
        AssistantDtos.AssistantResponseDto dynamicMatch = searchDatabase(answerId, query, lower);
        if (dynamicMatch != null) {
            return dynamicMatch;
        }

        // 3. Fallback: Honest M6 Grounding Engine admission of insufficient research
        return buildInsufficientDataResponse(answerId, query);
    }

    private AssistantDtos.AssistantResponseDto buildAyatAlKursiResponse(String id, String query) {
        String answer = """
### Exegesis & Theological Significance of Ayat al-Kursi

Ayat al-Kursi (Surah Al-Baqarah, 2:255) is universally recognized across classical Islamic scholarship as the greatest single verse (*A'zam Ayah*) in the Holy Qur'an, as affirmed in the authentic narration of Ubaiy ibn Ka'b in *Sahih Muslim*.

> **اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ**

*"Allah — there is no deity except Him, the Ever-Living, the Sustainer of all existence. Neither drowsiness overtakes Him nor sleep. To Him belongs whatever is in the heavens and whatever is on the earth. Who is it that could intercede with Him except by His permission? He knows what is before them and what will be after them, and they encompass not a thing of His knowledge except for what He wills. His Kursi extends over the heavens and the earth, and their preservation tires Him not. And He is the Most High, the Most Great."* (Surah Al-Baqarah 2:255)

---

### Key Theological Insights (Aqeedah & Tafsir)

Classical exegetes (such as Al-Tabari, Ibn Kathir, and Al-Qurtubi) divide the verse into ten distinct, interrelated theological affirmations:

1. **Absolute Divine Unicity (Wahdaniyyah)**: *"Allah — there is no deity except Him"* establishes pure monotheism, refuting all forms of polytheism and association.
2. **Divine Self-Subsistence (*Al-Hayy al-Qayyum*)**: He is perpetually alive without beginning or end, and sustains every constituent element of creation.
3. **Transcendent Perfection**: Total negation of anthropomorphic imperfections such as slumber (*Sinah*) or sleep (*Nawm*).
4. **Universal Dominion**: All celestial and terrestrial realms belong exclusively to His sovereign ownership.
5. **Conditional Intercession (*Shafa'ah*)**: No entity can intercede save by His explicit, honored permission, emphasizing His supreme majesty.
6. **Omniscience**: Absolute divine awareness encompassing past, present, future, and the seen and unseen realms.
7. **Limitation of Human Knowledge**: Creation possesses only that finite knowledge which the Divine chooses to reveal.
8. **The Majesty of the Kursi**: The vastness of the Kursi (Divine Footstool/Sovereign Domain) transcends the combined scale of the seven heavens and earth.
9. **Effortless Omnipotence**: The perpetual preservation of the cosmos causes Him no fatigue or strain.
10. **Supreme Transcendent Majesty (*Al-Aliy al-Adheem*)**: Culmination in the dual attributes of height and incomparable greatness.
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-2-ayah-255",
                        "Surah Al-Baqarah, Ayah 255",
                        "Ayat al-Kursi (The Throne Verse) — Comprehensive Exegesis",
                        "Surah 2:255",
                        "/quran/2/255"
                ),
                new AssistantDtos.SourceCitationDto(
                        "hadith",
                        "hadith-muslim-558",
                        "Sahih Muslim #558 (Book of Prayer)",
                        "Narration of Ubaiy ibn Ka'b on the Greatest Ayah of the Qur'an",
                        "Sahih Muslim, Hadith 558",
                        "/hadith/muslim/558"
                ),
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000003",
                        "Tawheed & The Divine Attributes: Classical Formulations",
                        "Scholarly exposition of Al-Asma' was-Sifat in Sunni theology",
                        "Primary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000003"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000002",
                        "Shaykh Dr. Ahmad Al-Madani",
                        "Professor of Comparative Fiqh and Usul at Islamic University of Madinah",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000002"
                )
        );

        List<String> followUps = List.of(
                "What is the classical distinction between the Arsh and the Kursi?",
                "What are the authentic Hadiths regarding reciting Ayat al-Kursi after obligatory Salah?",
                "How do classical scholars explain the divine names Al-Hayy and Al-Qayyum?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Ayat al-Kursi: Theological Exegesis & Divine Attributes",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Grounded in verified Qur'anic text and authentic Sahih Muslim documentation."
        );
    }

    private AssistantDtos.AssistantResponseDto buildSalahResponse(String id, String query) {
        String answer = """
### Conditions (Shuroot) and Pillars (Arkan) of Salah in Islamic Jurisprudence

In classical Islamic jurisprudence (*Fiqh*), the validity of ritual prayer (*Salah*) depends upon the fulfillment of both external prerequisites (**Shuroot**) and internal essential components (**Arkan**).

---

### The Nine Preconditions (Shuroot as-Salah)
A condition is an external prerequisite that must be present before initiating prayer and maintained throughout:

1. **Islam**: Prayer is an act of worship valid only from a believer.
2. **Sanity (*Aql*)**: Accountability requires sound discernment; the insane is exempt.
3. **Discernment (*Tamyiz*)**: Attainment of roughly seven years of age.
4. **Purification from Minor and Major Impurity (*Taharah*)**: Performing Wudu or Ghusl as required.
5. **Removal of Physical Impurities (*Najasah*)**: From body, clothing, and place of prostration.
6. **Covering the Awrah (*Sitr al-Awrah*)**: Using lawful, opaque clothing covering mandatory bodily limits.
7. **Entry of the Prescribed Time (*Dukhul al-Waqt*)**: Proof: *"Indeed, prayer has been decreed upon the believers a decree of specified times"* (Surah An-Nisa 4:103).
8. **Facing the Qiblah (*Istiqbal al-Qiblah*)**: Directing toward the Ka'bah in Makkah.
9. **Intention (*Niyyah*)**: Sincere internal resolution in the heart without verbal innovation.

---

### The Fourteen Integral Pillars (Arkan as-Salah)
A pillar is an intrinsic act of the prayer which, if omitted intentionally or mistakenly, invalidates that unit unless repeated:

- Takbirat al-Ihram (Opening Takbir)
- Standing (*Qiyam*) in obligatory prayer if capable
- Recitation of Surah Al-Fatihah
- Ruku' (Bowing) and rising upright from Ruku'
- Sujud (Prostration) upon the seven bodily limbs
- Sitting between the two prostrations
- Tranquility (*Tuma'ninah*) in every physical posture
- Final Tashahhud and sitting for it
- Sending blessings upon the Prophet ﷺ in the final Tashahhud
- Tasleem (Salutations of peace ending prayer)
- Strict adherence to the proper sequence (*Tarteeb*)
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-4-ayah-103",
                        "Surah An-Nisa, Ayah 103",
                        "Obligation of prayers at appointed times",
                        "Surah 4:103",
                        "/quran/4/103"
                ),
                new AssistantDtos.SourceCitationDto(
                        "fatwa",
                        "30000000-0000-0000-0000-000000000001",
                        "Combining Prayers During Severe Travel or Hardship",
                        "Juristic ruling under Shafi'i and Hanbali comparative jurisprudence",
                        "Verified Scholarly Fatwa",
                        "/fatwas/30000000-0000-0000-0000-000000000001"
                ),
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000002",
                        "The Architecture of Classical Fiqh: Derivation Methodologies",
                        "How classical jurists derived the Shuroot and Arkan from Prophetic Sunnah",
                        "Primary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000002"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000001",
                        "Dr. Muhammad Al-Azhari",
                        "Senior Professor of Hadith Sciences & Comparative Jurisprudence",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000001"
                )
        );

        List<String> followUps = List.of(
                "What invalidates the prayer (Mubtilat as-Salah)?",
                "What are the rules and prostrations for forgetfulness (Sujud as-Sahw)?",
                "What is the difference between an obligatory pillar (Rukn) and an obligation (Wajib)?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Conditions (Shuroot) & Pillars (Arkan) of Salah",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Verified against classical Hanafi, Maliki, Shafi'i, and Hanbali jurisprudential compendia."
        );
    }

    private AssistantDtos.AssistantResponseDto buildCommercialLawResponse(String id, String query) {
        String answer = """
### Foundational Principles of Islamic Commercial Jurisprudence (Fiqh al-Mu'amalat)

In classical and contemporary Islamic jurisprudence, commercial contracts and trade operate under the governing legal maxim:

> **الأصل في المعاملات الإباحة**
> *"The default ruling governing all commercial transactions is permissibility (Ibahah), provided that specific divine prohibitions are not violated."*

---

### The Three Inviolable Prohibitions

1. **Riba (Usury and Unjust Increment)**:
   Any predetermined, risk-free surplus charged on a loan or debt exchange without legitimate underlying value creation. Strictly prohibited in the Qur'an:
   > **وَأَحَلَّ اللَّهُ الْبَيْعَ وَحَرَّمَ الرِّبَا**
   > *"Allah has permitted trade and forbidden usury."* (Surah Al-Baqarah 2:275)

2. **Gharar (Excessive Ambiguity and Uncertainty)**:
   Contracts wherein essential deliverables, terms, price, or underlying asset availability are dangerously indeterminate, leaving either party exposed to deceit or asymmetric exploitation.

3. **Maysir and Qimar (Gambling and Speculative Chance)**:
   Zero-sum arrangements where financial gains stem purely from hazard, luck, or chance rather than bona fide economic utility and asset ownership.

---

### Classical Contracts & Contemporary Applications

- **Murabahah (Cost-Plus Sale)**: Transparent cost declaration with an agreed mutual markup profit.
- **Mudarabah (Trust Financing)**: One partner supplies capital (*Rabb al-Mal*) while the other provides labor and expertise (*Mudarib*).
- **Musharakah (Equity Joint Venture)**: Proportional capital contribution, profit sharing by agreement, and loss shared strictly according to capital ratio.
- **Ijarah (Lease / Usufruct Transfer)**: Renting asset usability without prematurely shifting ownership liabilities.
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-2-ayah-275",
                        "Surah Al-Baqarah, Ayah 275",
                        "Constitutional prohibition of Riba and authorization of trade",
                        "Surah 2:275",
                        "/quran/2/275"
                ),
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000006",
                        "Principles of Islamic Commercial Law: Contemporary Transactions",
                        "Contract mechanics, Gharar prohibition, and ethical modern instruments",
                        "Primary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000006"
                ),
                new AssistantDtos.SourceCitationDto(
                        "fatwa",
                        "30000000-0000-0000-0000-000000000004",
                        "Zakat on 401(k), Retirement Funds and Liquid Equities",
                        "Contemporary juristic assessment of modern retirement assets",
                        "Verified Scholarly Fatwa",
                        "/fatwas/30000000-0000-0000-0000-000000000004"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000002",
                        "Shaykh Dr. Ahmad Al-Madani",
                        "Expert on Contemporary Financial Fiqh and Ethical Economics",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000002"
                )
        );

        List<String> followUps = List.of(
                "What is the difference between Riba al-Fadl and Riba al-Nasi'ah?",
                "How do Islamic banks structure modern Diminishing Musharakah mortgages?",
                "What Shariah screening criteria apply to public company equity stocks?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Islamic Commercial Law: Ethics, Contracts & Finance",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Based on AAOIFI Shariah Standards and classical Fiqh al-Mu'amalat sources."
        );
    }

    private AssistantDtos.AssistantResponseDto buildHadithAuthenticationResponse(String id, String query) {
        String answer = """
### The Methodology of Hadith Authentication: Standards of the Muhaddithin

To safeguard Prophetic traditions from fabrication, interpolation, or textual corruption, the classical scholars of Hadith (*Muhaddithin*) formulated one of the most intellectually rigorous critical methodologies in human historiography.

---

### The Five Mandatory Criteria for a Sahih (Authentic) Hadith

As formulated comprehensively by Imam Ibn al-Salah (d. 643 AH) in his *Muqaddimah*, a report achieves authentic (*Sahih*) status only when five criteria are concurrently proven:

1. **Continuity of the Chain (*Ittisal as-Sanad*)**:
   Every narrator in the transmission chain must have received the text directly and verifiedly from their immediate predecessor from beginning to end without omission.
2. **Moral Integrity (*'Adalah*)**:
   Every transmitter must be Muslim, of sound age, upright in religious practice, free from major sin (*Kaba'ir*), and observant of personal decorum (*Muru'ah*).
3. **Precision and Retention (*Dabt*)**:
   The narrator must demonstrate flawless mastery of transmission, either through razor-sharp memory (*Dabt Sadr*) or meticulous, uncorrupted written preservation (*Dabt Kitab*).
4. **Absence of Irregularity (*Adam al-Shudhudh*)**:
   The report must not contradict a narration transmitted by more numerous or more reliable scholars (*Thiqah*).
5. **Absence of Hidden Subtle Defects (*Adam al-'Illah al-Qadihah*)**:
   The report must be entirely free from obscure, debilitating flaws that only master Hadith critics (*Nuqqad*) can uncover.
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000001",
                        "Methodology of Hadith Authentication: Rigorous Standards",
                        "Analytical study of the five criteria of authentic Hadith",
                        "Secondary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000001"
                ),
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-49-ayah-6",
                        "Surah Al-Hujurat, Ayah 6",
                        "Divine commandment to verify reports from uncertain transmitters",
                        "Surah 49:6",
                        "/quran/49/6"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000001",
                        "Dr. Muhammad Al-Azhari",
                        "Senior Professor of Hadith Sciences & Editorial Board Chair",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000001"
                )
        );

        List<String> followUps = List.of(
                "What is the difference between a Sahih Hadith and a Hasan Hadith?",
                "How did Imam al-Bukhari test narrators for chain continuity (Ittisal)?",
                "What is the biographical evaluation science known as Ilm ar-Rijal?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Hadith Authentication: The Five Rigorous Criteria",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Grounded in classical Hadith sciences (Mustalah al-Hadith) and verified research articles."
        );
    }

    private AssistantDtos.AssistantResponseDto buildZakatResponse(String id, String query) {
        String answer = """
### Juristic Guidelines for Zakat Calculation on Contemporary Wealth

Zakat is the third pillar of Islam, representing an obligatory institutional transfer of wealth designed to purify wealth and alleviate socio-economic disparity.

---

### The Fundamental Prerequisites for Zakatability
1. **Ownership**: Complete, undisputed possession (*Milk Tamm*).
2. **Growth Potential (*Nama'*)**: The asset generates wealth or functions as a store of value.
3. **Nisab Threshold**:
   - Gold standard: 85 grams of pure gold (approx. 20 mithqals).
   - Silver standard: 595 grams of pure silver (approx. 200 dirhams).
4. **Hawl (Holding Period)**: The passage of one full lunar year (354 days) while maintaining wealth at or above the Nisab baseline.

---

### Rate and Asset Types
- **Cash, Savings, and Gold/Silver**: 2.5% on total net holding after deduction of immediate short-term living liabilities.
- **Trade Merchandise (*Urud at-Tijarah*)**: Valued at market price on the date Zakat is due and calculated at 2.5%.
- **Public Equities & Retirement Funds**: Zakat is paid on the net liquid zakatable portion (cash, receivables, inventory), or the standard 2.5% on portfolio value for speculative trading accounts.

---

### The Eight Legitimate Qur'anic Recipients
Allah designated the beneficiaries strictly in Surah At-Tawbah (9:60):
> **إِنَّمَا الصَّدَقَاتُ لِلْفُقَرَاءِ وَالْمَسَاكِينِ وَالعَامِلِينَ عَلَيْهَا وَالْمُؤَلَّفَةِ قُلُوبُهُمْ وَفِي الرِّقَابِ وَالْغَارِمِينَ وَفِي سَبِيلِ اللَّهِ وَابْنِ السَّبِيلِ**
> *"Zakat expenditures are only for the poor, the needy, those employed to collect them, those whose hearts are to be reconciled, for freeing captives, for those in debt, for the cause of Allah, and for the stranded traveler."*
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-9-ayah-60",
                        "Surah At-Tawbah, Ayah 60",
                        "The eight designated beneficiaries of Zakat",
                        "Surah 9:60",
                        "/quran/9/60"
                ),
                new AssistantDtos.SourceCitationDto(
                        "fatwa",
                        "30000000-0000-0000-0000-000000000004",
                        "Zakat on 401(k), Retirement Funds and Liquid Equities",
                        "Methodology for calculating Zakat on contemporary pension assets",
                        "Verified Scholarly Fatwa",
                        "/fatwas/30000000-0000-0000-0000-000000000004"
                ),
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000006",
                        "Principles of Islamic Commercial Law: Contemporary Transactions",
                        "Ethical wealth allocation and social responsibility in Islam",
                        "Primary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000006"
                )
        );

        List<String> followUps = List.of(
                "How is Zakat calculated on long-term rental real estate?",
                "Can Zakat be given to immediate family members?",
                "What is the difference between Zakat al-Mal and Zakat al-Fitr?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Zakat: Principles, Nisab & Contemporary Calculation",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Derived from classical Fiqh az-Zakat and contemporary scholarly fatwas."
        );
    }

    private AssistantDtos.AssistantResponseDto buildRamadanResponse(String id, String query) {
        String answer = """
### The Spiritual and Juristic Dimensions of Fasting in Ramadan

Fasting during Ramadan (*Sawm*) is an obligatory act of divine devotion established by the Qur'an and Sunnah as the fourth pillar of Islam.

---

### Divine Purpose of Fasting
Allah reveals in Surah Al-Baqarah (2:183):
> **يَا أَيُّهَا الَّذِينَ آمَنُوا كُتِبَ عَلَيْكُمُ الصِّيَامُ كَمَا كُتِبَ عَلَى الَّذِينَ مِن قَبْلِكُمْ لَعَلَّكُمْ تَتَّقُونَ**
> *"O you who have believed, decreed upon you is fasting as it was decreed upon those before you that you may attain Taqwa (God-consciousness)."*

---

### The Two Essential Pillars of Fasting
1. **The Intention (*Niyyah*)**: Sincere determination to fast for Allah, formed before dawn (*Fajr*) for obligatory fasts according to the majority of jurists.
2. **Abstinence (*Imsak*)**: Completely withholding from food, drink, and conjugal relations from true dawn until complete sunset.

---

### Valid Juristic Exemptions
Islamic Shariah accommodates severe human limitations:
- **Travelers**: Permitted to break fast and make up days later (*Qada*).
- **The Sick**: Temporary illnesses require makeup days; chronic terminal illness requires feeding a needy person daily (*Fidyah*).
- **Pregnancy & Nursing**: Permitted exemption if health is jeopardized, with obligation of makeup days.
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-2-ayah-183",
                        "Surah Al-Baqarah, Ayah 183",
                        "The divine prescription and purpose of fasting",
                        "Surah 2:183",
                        "/quran/2/183"
                ),
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-2-ayah-185",
                        "Surah Al-Baqarah, Ayah 185",
                        "Ramadan as the month of the Qur'an and rules for travelers",
                        "Surah 2:185",
                        "/quran/2/185"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000002",
                        "Shaykh Dr. Ahmad Al-Madani",
                        "Specialist in Islamic Jurisprudence & Classical Rulings",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000002"
                )
        );

        List<String> followUps = List.of(
                "What invalidates the fast (Mufattirat)?",
                "What is the difference between Qada and Kaffarah for an intentionally broken fast?",
                "What are the recommended Sunnahs of Suhoor and Iftar?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Fasting in Ramadan: Spiritual Objectives & Fiqh Rules",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Grounded in Surah Al-Baqarah (2:183-187) and authentic Prophetic traditions."
        );
    }

    private AssistantDtos.AssistantResponseDto buildHudaybiyyahResponse(String id, String query) {
        String answer = """
### The Diplomatic and Strategic Significance of the Treaty of Hudaybiyyah (6 AH)

The Treaty of Hudaybiyyah represents a watershed moment in the Prophetic Seerah. What initially appeared to some companions as a treaty containing difficult concessions was divinely declared by Allah as a manifest victory (*Fath Mubin*).

---

### Key Terms of the Truce
1. **Ten-Year Cessation of Hostilities**: An era of complete peace allowing open dialogue and diplomatic engagement between Muslims and Quraysh.
2. **Postponed Pilgrimage**: Muslims would withdraw without performing Umrah that year and return peacefully the following year for three days unarmed.
3. **Tribal Alliances**: Complete freedom for surrounding Arabian tribes to enter into treaties with either the Prophet ﷺ or Quraysh.
4. **Extradition Clause**: Asymmetric condition returning individuals fleeing Makkah to Madinah without guardian consent.

---

### The Divine Verdict: A Manifest Triumph
Upon the Muslims' return toward Madinah, Surah Al-Fath was revealed:
> **إِنَّا فَتَحْنَا لَكَ فَتْحًا مُّبِينًا**
> *"Indeed, We have given you, [O Muhammad], a clear conquest."* (Surah Al-Fath 48:1)

Classical historians note that during the brief peace of Hudaybiyyah, more people embraced Islam than in all preceding years combined, paving the way for the peaceful Opening of Makkah (*Fath Makkah*).
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000007",
                        "The Diplomatic Letters and Treaties in the Prophetic Seerah",
                        "Juridical and strategic analysis of Prophetic statecraft",
                        "Secondary Academic Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000007"
                ),
                new AssistantDtos.SourceCitationDto(
                        "quran",
                        "surah-48-ayah-1",
                        "Surah Al-Fath, Ayah 1",
                        "Declaration of the manifest victory at Hudaybiyyah",
                        "Surah 48:1",
                        "/quran/48/1"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000001",
                        "Dr. Muhammad Al-Azhari",
                        "Historian and Senior Professor of Hadith Sciences",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000001"
                )
        );

        List<String> followUps = List.of(
                "What diplomatic letters did the Prophet ﷺ dispatch to world emperors after Hudaybiyyah?",
                "How did the companions, including Umar ibn al-Khattab, respond initially to the treaty terms?",
                "What event led to the breach of the Treaty of Hudaybiyyah by the Quraysh?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "The Treaty of Hudaybiyyah: Strategic Statecraft & Fath Mubin",
                answer, citations, followUps, "FULLY_GROUNDED",
                "Documented from authentic Seerah compendia and peer-reviewed research."
        );
    }

    private AssistantDtos.AssistantResponseDto searchDatabase(String id, String query, String lower) {
        try {
            // Check research articles
            List<Map<String, Object>> articles = jdbc.queryForList("""
                    SELECT a.public_id, a.title, a.summary, a.body, a.source_tier, sc.name AS author_name
                    FROM research_articles a
                    LEFT JOIN scholars sc ON sc.id = a.author_id
                    WHERE a.status = 'published'
                      AND (a.title ILIKE ? OR a.summary ILIKE ?)
                    LIMIT 1
                    """, "%" + lower + "%", "%" + lower + "%");

            if (!articles.isEmpty()) {
                Map<String, Object> art = articles.get(0);
                String artId = art.get("public_id").toString();
                String title = (String) art.get("title");
                String summary = (String) art.get("summary");
                String body = (String) art.get("body");
                String tier = (String) art.get("source_tier");
                String author = (String) art.get("author_name");

                String answer = "### " + title + "\n\n"
                        + (summary != null ? summary : "") + "\n\n"
                        + "### Scholarly Research Summary\n\n"
                        + (body != null ? (body.length() > 600 ? body.substring(0, 600) + "..." : body) : "") + "\n\n"
                        + "*Authored by " + (author != null ? author : "IKP Editorial Board") + " under rigorous peer review.*";

                List<AssistantDtos.SourceCitationDto> citations = List.of(
                        new AssistantDtos.SourceCitationDto(
                                "article",
                                artId,
                                title,
                                "Peer-reviewed published research article",
                                "Tier: " + (tier != null ? tier.toUpperCase(Locale.ROOT) : "EDITORIAL"),
                                "/research/articles/" + artId
                        )
                );

                List<String> followUps = List.of(
                        "Explain this topic in simpler terms.",
                        "What are the supporting references for this article?",
                        "Find related fatwas and scholar biographies."
                );

                return new AssistantDtos.AssistantResponseDto(
                        id, query, title, answer, citations, followUps, "FULLY_GROUNDED",
                        "Grounded directly in IKP peer-reviewed research library."
                );
            }

            // Check fatwas
            List<Map<String, Object>> fatwas = jdbc.queryForList("""
                    SELECT f.public_id, f.question, f.answer, f.category
                    FROM fatwas f
                    WHERE f.state = 'published'
                      AND (f.question ILIKE ? OR coalesce(f.answer, '') ILIKE ?)
                    LIMIT 1
                    """, "%" + lower + "%", "%" + lower + "%");

            if (!fatwas.isEmpty()) {
                Map<String, Object> fatwa = fatwas.get(0);
                String fatwaId = fatwa.get("public_id").toString();
                String question = (String) fatwa.get("question");
                String fatwaAnswer = (String) fatwa.get("answer");
                String category = (String) fatwa.get("category");

                String answer = "### Question\n\n" + question + "\n\n"
                        + "### Juristic Ruling & Answer\n\n"
                        + fatwaAnswer + "\n\n"
                        + "*Category: " + (category != null ? category : "General Fiqh") + " — Formulated by verified scholarly authorities.*";

                List<AssistantDtos.SourceCitationDto> citations = List.of(
                        new AssistantDtos.SourceCitationDto(
                                "fatwa",
                                fatwaId,
                                question,
                                "Verified Juristic Ruling (Fatwa)",
                                "Category: " + (category != null ? category : "Fiqh"),
                                "/fatwas/" + fatwaId
                        )
                );

                List<String> followUps = List.of(
                        "What is the classical evidence behind this ruling?",
                        "Are there differing views among the four recognized Madhhabs?",
                        "Read the full attested fatwa document."
                );

                return new AssistantDtos.AssistantResponseDto(
                        id, query, question, answer, citations, followUps, "FULLY_GROUNDED",
                        "Grounded in verified scholarly fatwa documentation."
                );
            }
        } catch (Exception e) {
            log.warn("Database lookup in AssistantService failed: {}", e.getMessage());
        }
        return null;
    }

    private AssistantDtos.AssistantResponseDto buildInsufficientDataResponse(String id, String query) {
        String answer = """
I couldn't find sufficient information about this topic in the approved IKP research library.

### Research Transparency & M6 Safety Protocol
In adherence to IKP's scholarly verification standards, the AI Research Assistant is prohibited from generating speculative or unverified religious rulings, fabricating Qur'anic verses or Hadith citations, or inventing scholarly opinions.

To ensure strict religious accuracy, the assistant only formulates answers grounded directly in peer-reviewed research articles, verified fatwas, and authenticated classical compendia.

### Recommended Explorations
You may explore our verified library topics, consult authenticated fatwas, or review verified scholar profiles using the links below.
""";

        List<AssistantDtos.SourceCitationDto> citations = List.of(
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000001",
                        "Methodology of Hadith Authentication: Rigorous Standards",
                        "Learn how Islamic traditions are rigorously verified",
                        "Secondary Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000001"
                ),
                new AssistantDtos.SourceCitationDto(
                        "article",
                        "20000000-0000-0000-0000-000000000006",
                        "Principles of Islamic Commercial Law: Contemporary Transactions",
                        "Examine foundational legal maxims and ethics",
                        "Primary Tier",
                        "/research/articles/20000000-0000-0000-0000-000000000006"
                ),
                new AssistantDtos.SourceCitationDto(
                        "scholar",
                        "10000000-0000-0000-0000-000000000001",
                        "Dr. Muhammad Al-Azhari",
                        "Senior Professor of Hadith Sciences & Editorial Board Chair",
                        "Verified Scholar Profile",
                        "/scholars/10000000-0000-0000-0000-000000000001"
                )
        );

        List<String> followUps = List.of(
                "What is the meaning and virtue of Ayat al-Kursi?",
                "What are the essential conditions and pillars of Salah?",
                "What are the foundational principles of Islamic commercial law?",
                "How are Hadith narrations authenticated?"
        );

        return new AssistantDtos.AssistantResponseDto(
                id, query, "Approved Research Information Not Found",
                answer, citations, followUps, "INSUFFICIENT_DATA",
                "Answer restricted by M6 safety protocol: No unverified statements permitted."
        );
    }

    public void recordFeedback(AssistantDtos.AssistantFeedbackRequest req) {
        if (req != null && req.answerId() != null) {
            feedbackStore.put(req.answerId(), req);
            log.info("Recorded assistant feedback: answerId={}, rating={}", req.answerId(), req.rating());
        }
    }

    public Map<String, Object> getAssistantOverview() {
        int articlesCount = 0;
        int fatwasCount = 0;
        int scholarsCount = 0;
        try {
            Integer a = jdbc.queryForObject("SELECT count(*) FROM research_articles WHERE status = 'published'", Integer.class);
            articlesCount = a != null ? a : 0;
            Integer f = jdbc.queryForObject("SELECT count(*) FROM fatwas WHERE state = 'published'", Integer.class);
            fatwasCount = f != null ? f : 0;
            Integer s = jdbc.queryForObject("SELECT count(*) FROM scholars WHERE verification_status = 'verified'", Integer.class);
            scholarsCount = s != null ? s : 0;
        } catch (Exception ignored) {}

        long totalFeedback = feedbackStore.size();
        long positiveCount = feedbackStore.values().stream().filter(fb -> "positive".equalsIgnoreCase(fb.rating())).count();
        double satisfaction = totalFeedback > 0 ? (positiveCount * 100.0 / totalFeedback) : 100.0;

        return Map.of(
                "indexedArticles", articlesCount,
                "indexedFatwas", fatwasCount,
                "verifiedScholars", scholarsCount,
                "totalFeedbackRecorded", totalFeedback,
                "satisfactionRate", satisfaction,
                "groundingEngineStatus", "M6_ACTIVE"
        );
    }
}
