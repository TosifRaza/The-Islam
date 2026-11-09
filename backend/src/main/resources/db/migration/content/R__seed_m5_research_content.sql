-- R__seed_m5_research_content.sql
-- Seed curated research topics, peer-reviewed articles, verified scholars, and authenticated fatwas.
-- Repeatable Flyway migration: automatically applied by Spring Boot backend on boot.
-- Uses ON CONFLICT and deterministic UUIDs so it is safely idempotent.

-- 0. FOUNDATIONAL SURAH 1 (Al-Fatihah) FOR VERIFIED CITATIONS ----------------
INSERT INTO quran_surahs (id, name_ar, name_transliterated, name_en, revelation_place, revelation_order, ayah_count, bismillah_prefix)
VALUES (1, 'الفاتحة', 'Al-Fatihah', 'The Opener', 'makki', 5, 7, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quran_ayahs (surah_id, ayah_number, text_uthmani, juz, sajdah) VALUES
    (1, 1, 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ', 1, false),
    (1, 2, 'الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ', 1, false),
    (1, 3, 'الرَّحْمَٰنِ الرَّحِيمِ', 1, false),
    (1, 4, 'مَالِكِ يَوْمِ الدِّينِ', 1, false),
    (1, 5, 'إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ', 1, false),
    (1, 6, 'اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ', 1, false),
    (1, 7, 'صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ', 1, false)
ON CONFLICT (surah_id, ayah_number) DO NOTHING;

-- 1. TOPICS -------------------------------------------------------------------
INSERT INTO research_topics (slug, name, name_ar, description, sort_order, status) VALUES
    ('quran',                'Quranic Sciences',       'علوم القرآن',       'Thematic exegesis, revelation contexts, and linguistic rhetoric', 1,  'active'),
    ('hadith',               'Hadith & Sunnah',        'الحديث والسنة',     'Methodology of authentication, narrator criticism, and collection analysis', 2,  'active'),
    ('fiqh',                 'Islamic Jurisprudence',  'الفقه الإسلامي',    'Practical rulings of worship, transactions, and contemporary living', 3,  'active'),
    ('usul_al_fiqh',         'Usul al-Fiqh',           'أصول الفقه',        'Legal theory, textual derivation methods, and jurisprudential maxims', 4,  'active'),
    ('aqeedah',              'Islamic Aqeedah',        'العقيدة الإسلامية', 'Six pillars of Iman, Tawheed, and classical orthodox creed', 5,  'active'),
    ('islamic_history',      'Islamic Civilization',   'التاريخ والحضارة',  'Heritage, intellectual milestones, and institutional developments', 6,  'active'),
    ('seerah',               'Seerah of the Prophet ﷺ','السيرة النبوية',    'Historical analysis of the Prophetic biography and character', 7,  'active'),
    ('arabic_language',      'Arabic Philology',       'اللغة والأدب',      'Morphology, syntax, and classical semantics', 8,  'active'),
    ('classical_literature', 'Classical Literature',   'الأدب الكلاسيكي',   'Classical Islamic literary heritage and scholastic texts', 9,  'active'),
    ('comparative_studies',  'Comparative Studies',    'الدراسات المقارنة', 'Comparative madhhab studies, theology, and ethical inquiries', 10, 'active')
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    name_ar = EXCLUDED.name_ar,
    description = EXCLUDED.description,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status;

-- 2. SYSTEM USER FOR CONTENT AUTHORSHIP ---------------------------------------
DO $$
DECLARE
    v_user_id BIGINT;
    v_reviewer_id BIGINT;
BEGIN
    SELECT id INTO v_user_id FROM users WHERE email = 'content.editor@ikp.dev' LIMIT 1;
    IF v_user_id IS NULL THEN
        INSERT INTO users (email, status, deletion_state, language_pref)
        VALUES ('content.editor@ikp.dev', 'active', 'none', 'en')
        RETURNING id INTO v_user_id;
    END IF;

    SELECT id INTO v_reviewer_id FROM users WHERE email = 'scholar.reviewer@ikp.dev' LIMIT 1;
    IF v_reviewer_id IS NULL THEN
        INSERT INTO users (email, status, deletion_state, language_pref)
        VALUES ('scholar.reviewer@ikp.dev', 'active', 'none', 'en')
        RETURNING id INTO v_reviewer_id;
    END IF;

    -- 3. SCHOLARS (Explicitly identified demonstration academic profiles) ------
    INSERT INTO scholars (
        id, public_id, name, name_ar, biography, qualifications, areas_of_expertise,
        verification_status, user_id, verified_at, created_at, updated_at
    ) OVERRIDING SYSTEM VALUE VALUES
        (101, '10000000-0000-0000-0000-000000000001',
         'Dr. Muhammad Al-Azhari', 'د. محمد الأزهري',
         'Senior researcher in classical hadith verification methodologies and narrator criticism (Rijal). Serves as an academic fellow with over twenty published monographs on the textual integrity of the Sahihayn. [Demonstration Academic Profile for IKP Platform testing].',
         'Ph.D. in Usul al-Hadith, Al-Azhar University; Faculty Member in Islamic Studies (Demo Profile)',
         'Hadith Sciences, Narrator Criticism (Ilm ar-Rijal), Comparative Fiqh',
         'verified', v_reviewer_id, now(), now(), now()),

        (102, '10000000-0000-0000-0000-000000000002',
         'Shaykh Dr. Ahmad Al-Madani', 'الشيخ د. أحمد المدني',
         'Specialist in classical jurisprudential maxims (Qawa''id Fiqhiyyah) and their contemporary applications in ethical commerce, contracts, and digital finance. [Demonstration Academic Profile].',
         'Doctorate in Shariah & Usul al-Fiqh, Islamic University of Madinah; Advisor in Islamic Banking (Demo Profile)',
         'Principles of Jurisprudence (Usul al-Fiqh), Islamic Banking, Financial Contracts',
         'verified', NULL, now(), now(), now()),

        (103, '10000000-0000-0000-0000-000000000003',
         'Dr. Fatima Al-Zahra', 'د. فاطمة الزهراء',
         'Author of seminal treatises examining thematic coherence in Makkan Surahs and structural analysis of Quranic rhetoric. Frequent speaker on Islamic family ethics and Quranic contemplation. [Demonstration Academic Profile].',
         'Ph.D. in Quranic Sciences & Classical Arabic Linguistics, Umm Al-Qura University (Demo Profile)',
         'Quranic Exegesis (Tafsir), Classical Arabic Philology, Family Ethics',
         'verified', NULL, now(), now(), now())
    ON CONFLICT (public_id) DO UPDATE SET
        name = EXCLUDED.name,
        name_ar = EXCLUDED.name_ar,
        biography = EXCLUDED.biography,
        qualifications = EXCLUDED.qualifications,
        areas_of_expertise = EXCLUDED.areas_of_expertise,
        verification_status = EXCLUDED.verification_status;

    -- 4. RESEARCH ARTICLES ---------------------------------------------------
    INSERT INTO research_articles (
        id, public_id, topic_id, title, title_ar, summary, body, language,
        author_display_name, source_tier, status, created_by, published_at, created_at, updated_at
    ) OVERRIDING SYSTEM VALUE VALUES
        (201, '20000000-0000-0000-0000-000000000001',
         (SELECT id FROM research_topics WHERE slug = 'hadith' LIMIT 1),
         'Methodology of Hadith Authentication: Rigorous Standards of the Muhaddithin',
         'منهجية توثيق الحديث الشريف عند أئمة الحديث',
         'An analytical study of the five criteria of hadith authenticity (Ittisal as-Sanad, Adalah, Dabt, Adam ash-Shudhudh, and Adam al-Illah) and the empirical precision developed by classical scholars.',
         '### Introduction: The Science of Transmission

The preservation of the prophetic tradition (Sunnah) stands as a unique intellectual and empirical monument in the history of human scholarship. The scholars of Hadith (Muhaddithin) formulated a comprehensive epistemological framework to evaluate both the chain of transmitters (Isnad) and the content of the transmission (Matn).

### The Five Universal Conditions for Authenticity (Sahih)

According to the classical consensus articulated by Imam Ibn al-Salah (d. 643 AH) in his *Muqaddimah*, a narration achieves the designation of *Sahih* (sound) only when five rigorous, concurrent criteria are met:

1. **Continuous Sanad (Ittisal as-Sanad):** Every single narrator in the chain must have received the text directly from their immediate predecessor from beginning to end without interruption.
2. **Moral Integrity (Adalah):** Each narrator must be an adult Muslim of upright character, free from open sinfulness, heresy, and violations of modesty.
3. **Mnemonic Accuracy (Dabt):** The transmitter must demonstrate flawless preservation of the text, either through exact photographic memory (Dabt as-Sadr) or verified, preserved written codices (Dabt al-Kitab).
4. **Absence of Irregularity (Adam ash-Shudhudh):** The narration must not contradict the transmission of another narrator who is superior in reliability or backed by an established majority.
5. **Freedom from Hidden Defects (Adam al-Illah):** The report must be entirely free from subtle, concealed defects that only master critics can detect after collating all existing paths of transmission.

### Rigorous Standards of Narrator Verification

The scholars instituted the discipline of *Ilm ar-Rijal* (Biographical Evaluation), documenting the birth dates, travels, teachers, students, memory retention, and life circumstances of over 100,000 transmitters. This guaranteed that spurious or fabricated reports were quarantined and catalogued.

### Conclusion and Modern Significance

The methods established by Imam al-Bukhari, Imam Muslim, and their contemporaries predate modern historical-critical methodologies by centuries, providing a verified bedrock for Islamic legal and theological derivation.',
         'en', 'Dr. Muhammad Al-Azhari', 'secondary', 'published', v_user_id, now(), now(), now()),

        (202, '20000000-0000-0000-0000-000000000002',
         (SELECT id FROM research_topics WHERE slug = 'usul_al_fiqh' LIMIT 1),
         'The Maqasid (Higher Objectives) of Islamic Shariah',
         'مقاصد الشريعة الإسلامية ومفهوم المصلحة العامة',
         'Exploration of the essential objectives of divine legislation formulated by Imam al-Shatibi and Imam al-Ghazali: preservation of Religion, Life, Intellect, Lineage, and Wealth.',
         '### The Philosophical Grounding of Shariah Rulings

Islamic jurisprudence is not a mechanical aggregation of arbitrary injunctions; rather, every divine command and prohibition is oriented toward realizing human welfare (Maslahah) and averting harm (Mafsadah) in both this worldly life and the Hereafter.

### The Five Inviolable Necessities (Ad-Daruriyyat al-Khams)

Classical jurists, notably Imam Abu Hamid al-Ghazali (d. 505 AH) in *Al-Mustasfa* and Imam al-Shatibi (d. 790 AH) in *Al-Muwafaqat*, systematically categorized the universal objectives into five foundational necessities:

1. **Preservation of Faith (Hifdh ad-Deen):** Enacted through the protection of core creed, freedom of worship, and performance of obligations.
2. **Preservation of Human Life (Hifdh an-Nafs):** Manifested in the absolute sanctity of life, prohibition of murder, and mandates for bodily sustenance and medical care.
3. **Preservation of Intellect (Hifdh al-Aql):** Safeguarded through education, contemplation, and the absolute prohibition of intoxicants and corrupting substances.
4. **Preservation of Lineage and Honour (Hifdh an-Nasl wal-Ird):** Protected through lawful marriage, family cohesion, and severe prohibition of slander and adultery.
5. **Preservation of Property (Hifdh al-Mal):** Upheld through property rights, fair contracts, prohibition of usury (Riba), theft, and fraud.

### The Three Tiers of Welfare

The jurists further structured Maslahah into three hierarchical levels:
- **Necessities (Daruriyyat):** Elements without which societal life disintegrates into chaos.
- **Needs (Hajiyyat):** Provisions that alleviate hardship and promote ease, such as concessions during illness or travel.
- **Embellishments (Tahsiniyyat):** Noble manners, aesthetic purity, and refinement of character.

### Application in Contemporary Islamic Legal Reasoning

Understanding Maqasid provides modern scholars with the tools for Ijtihad when confronting novel bioethical, economic, and technological questions, ensuring that new rulings harmonize with the overarching wisdom of the Quran and Sunnah.',
         'en', 'Shaykh Dr. Ahmad Al-Madani', 'secondary', 'published', v_user_id, now(), now(), now()),

        (203, '20000000-0000-0000-0000-000000000003',
         (SELECT id FROM research_topics WHERE slug = 'quran' LIMIT 1),
         'Tafsir Methodology: Principles for Understanding the Noble Quran',
         'قواعد وأصول في تفسير القرآن العظيم',
         'A comprehensive treatise on the legitimate ranks of exegesis: Quran by Quran, Quran by authentic Sunnah, sayings of the Companions, and classical Arabic linguistic conventions.',
         '### Introduction: The Authority of the Divine Word

The Noble Quran is the direct speech of Allah, revealed in a clear Arabic tongue. Approaching its interpretation requires disciplined adherence to established hermeneutical principles to guard against subjective speculation (Tafsir bir-Ra''y al-Madhmum).

### The Primary Hierarchy of Exegesis (Tafsir bil-Ma''thur)

Imam Ibn Kathir and Shaykh al-Islam Ibn Taymiyyah outline the soundest methodological ranking as follows:

1. **Exegesis of Quran by Quran:** What is mentioned in brief in one verse is elaborated in detail elsewhere. For example, the "Path of those upon whom You have bestowed favor" (Surah Al-Fatihah 1:7) is explicitly elucidated in Surah An-Nisa (4:69) as the prophets, the truthful, the martyrs, and the righteous.
2. **Exegesis by the Authentic Sunnah:** The Messenger of Allah ﷺ was charged by Allah with explaining the revelation: *"And We sent down to you the message that you may make clear to the people what was sent down to them"* (Surah An-Nahl 16:44). The Prophet''s practical demonstrations and verbal sayings clarify general commands, such as prayer timings and zakat rates.
3. **The Explanations of the Sahabah (Companions):** Specifically scholars among them such as Abdullah ibn Abbas (the Translator of the Quran), Abdullah ibn Mas''ud, and Ali ibn Abi Talib, who witnessed the circumstances of revelation (Asbab an-Nuzul).
4. **The Understanding of the Tabi''in (Successors):** The premier students of the Companions in Makkah, Madinah, and Kufah.
5. **Classical Arabic Linguistics:** Interpreting words according to their pristine linguistic usage during the revelation era without anachronistic modern impositions.',
         'en', 'Dr. Fatima Al-Zahra', 'primary', 'published', v_user_id, now(), now(), now()),

        (204, '20000000-0000-0000-0000-000000000004',
         (SELECT id FROM research_topics WHERE slug = 'islamic_history' LIMIT 1),
         'Foundational Principles of Islamic Ethical Governance',
         'أصول الحكم الراشد والعدالة الاجتماعية في التاريخ الإسلامي',
         'An editorial analysis examining the constitutional compact of the Sahifah of Madinah, accountability (Shura), social welfare, and institutional checks in early Islamic administration.',
         '### The Madinan Constitution: Blueprint for Pluralism and Rule of Law

Upon his arrival in Madinah, the Prophet Muhammad ﷺ enacted the *Sahifah* (Constitution of Madinah), establishing a pioneering civic confederation founded upon mutual defense, justice, sanctity of life and property, and freedom of religious practice for Muslim and Jewish tribes alike.

### Core Pillars of Islamic Public Administration

1. **Justice as an Absolute Mandate (Al-Adl):** In Islam, justice is not a political expediency but a divine covenant. Allah commands: *"Indeed, Allah commands you to render trusts to whom they are due and when you judge between people to judge with justice"* (Surah An-Nisa 4:58).
2. **Mutual Consultation (Ash-Shura):** Collective governance requiring leadership to consult specialists and community representatives before enacting public policies.
3. **Fiduciary Accountability (Amanah):** Public office is an onerous trust, not a vehicle for self-enrichment or nepotism.
4. **Institutional Welfare (Bayt al-Mal):** Systematic distribution of resources ensuring that orphans, the impoverished, travelers, and the elderly receive dignified sustenance from public revenues.

### Historical Lessons for Contemporary Institutions

The governance model of the Rightly Guided Caliphs demonstrates that ethical integrity, transparency, and accessible courts form the indispensable prerequisites for enduring peace and social cohesion.',
         'en', 'Editorial Scholarly Board', 'editorial', 'published', v_user_id, now(), now(), now()),

        (205, '20000000-0000-0000-0000-000000000005',
         (SELECT id FROM research_topics WHERE slug = 'aqeedah' LIMIT 1),
         'The Comprehensive Concept of Tawheed in Islamic Theology',
         'مفهوم التوحيد الشامل وأقسامه في العقيدة الإسلامية',
         'A scholarly exposition of the foundational doctrine of Monotheism: Tawheed ar-Rububiyyah, Tawheed al-Uluhiyyah, and Tawheed al-Asma was-Sifat.',
         '### The Centrality of Tawheed

Tawheed (the absolute Oneness of Allah) is the bedrock of Islam and the core message communicated by every Prophet sent to humanity. It encompasses not merely acknowledging a supreme Creator, but singularly directing every act of devotion, hope, fear, and reliance to Allah alone.

### The Three Interconnected Categories

Classical scholars structured the understanding of divine unity into three non-negotiable dimensions:

1. **Tawheed ar-Rububiyyah (Oneness of Lordship):** Affirming Allah alone as the Creator, Sustainer, Provider, and Controller of the cosmos.
2. **Tawheed al-Uluhiyyah (Oneness of Worship):** Directing prayer, sacrifice, supplication (Dua), repentance, and vows solely to Allah. This was the primary axis of debate between the Messengers and their opponents.
3. **Tawheed al-Asma was-Sifat (Oneness of Names and Attributes):** Affirming all names and descriptions that Allah and His Messenger have affirmed without resemblance to creation (Tashbeeh), denial (Ta''teel), or arbitrary interpretation (Ta''weel). Allah says: *"There is nothing like unto Him, and He is the Hearing, the Seeing"* (Surah Ash-Shura 42:11).

### Theological Implications for Human Dignity

True monotheism liberates the human conscience from subservience to wealth, tyrants, and superstition, grounding the believer in profound spiritual peace and moral clarity.',
         'en', 'Dr. Muhammad Al-Azhari', 'primary', 'published', v_user_id, now(), now(), now())
    ON CONFLICT (public_id) DO UPDATE SET
        title = EXCLUDED.title,
        title_ar = EXCLUDED.title_ar,
        summary = EXCLUDED.summary,
        body = EXCLUDED.body,
        author_display_name = EXCLUDED.author_display_name,
        source_tier = EXCLUDED.source_tier,
        status = EXCLUDED.status;

    -- 5. RESEARCH ARTICLE CITATIONS (Verified References) ---------------------
    DELETE FROM research_article_citations WHERE article_id IN (201, 202, 203, 204, 205);

    INSERT INTO research_article_citations (
        article_id, citation_kind, surah_id, ayah_number, external_ref, quote, note, sort_order
    ) VALUES
        (201, 'external', NULL, NULL, 'Sahih al-Bukhari, Kitab Bad al-Wahy',
         'Actions are judged by their intentions...', 'Foundational hadith criterion on transmission', 1),
        (201, 'external', NULL, NULL, 'Ibn al-Salah, Muqaddimat Ibn al-Salah fi Ulum al-Hadith',
         'The definition of Sahih hadith requires continuous transmission by upright, precise narrators.', 'Standard classical criterion', 2),
        (202, 'external', NULL, NULL, 'Al-Shatibi, Al-Muwafaqat fi Usul ash-Shariah',
         'The primary intent of Shariah is the realization of welfare for creation.', 'Foundational text on Maqasid', 1),
        (202, 'external', NULL, NULL, 'Al-Ghazali, Al-Mustasfa min Ilm al-Usul',
         'The order of necessities begins with religion, then life, intellect, lineage, and wealth.', 'Classical hierarchy of necessities', 2),
        (203, 'external', NULL, NULL, 'Surah An-Nisa (4:59)',
         'O you who have believed, obey Allah and obey the Messenger and those in authority among you...', 'Textual basis for hermeneutical authority', 1),
        (203, 'external', NULL, NULL, 'Surah An-Nahl (16:44)',
         'And We sent down to you the message that you may make clear to the people what was sent down to them.', 'Proof for prophetic explanation of the Quran', 2),
        (204, 'external', NULL, NULL, 'Surah An-Nisa (4:58)',
         'Indeed, Allah commands you to render trusts to whom they are due and when you judge between people to judge with justice.', 'Constitutional mandate for judicial impartiality', 1),
        (205, 'external', NULL, NULL, 'Surah Ash-Shura (42:11)',
         'There is nothing like unto Him, and He is the Hearing, the Seeing.', 'Foundational theological principle of transcendence without anthropomorphism', 1),
        (205, 'external', NULL, NULL, 'Surah Al-Ikhlas (112:1-4)',
         'Say: He is Allah, [who is] One. Allah, the Eternal Refuge...', 'Complete articulation of Tawheed', 2);

    -- 6. FATWAS (Authenticated Rulings with Verified Sources) -----------------
    INSERT INTO fatwas (
        id, public_id, question, answer_draft, answer, category, language,
        source_refs, state, created_by, decided_by, decided_at, decision_notes,
        published_by, published_at, version, created_at, updated_at
    ) OVERRIDING SYSTEM VALUE VALUES
        (301, '30000000-0000-0000-0000-000000000001',
         'What is the ruling on combining prayers (Jam'') during severe rainfall, heavy storms, or travel?',
         'Combining prayers is permissible under established conditions.',
         'Praise be to Allah.

It is authentically established from the Sunnah that combining two prayers — specifically Dhuhr with Asr, and Maghrib with Isha — is a legal concession (Rukhsah) granted by Allah to alleviate hardship for the Muslim community.

### 1. During Travel (Safar)
When traveling a distance that constitutes legal travel (generally 80 km or more according to majority scholars), a traveler may combine Dhuhr and Asr together (either at the time of the earlier prayer or the later prayer) and Maghrib and Isha together. Shortening four-rak''ah prayers to two rak''ahs (Qasr) is also strongly recommended during travel.

### 2. During Severe Rain, Flood, or Hazardous Weather
The Prophet ﷺ permitted the people of Madinah to combine Maghrib and Isha at the mosque during intense rain that drenches clothing and makes walking to the congregation hazardous (Sahih al-Bukhari, Sahih Muslim).
- This concession specifically applies to congregational prayer at the mosque to spare worshippers from excessive mud, extreme cold, or dangerous conditions.
- Dhuhr and Asr may also be combined according to the Hanbali and Shafi''i schools if the rain presents severe difficulty.
- Performing the prayer at home without hardship does not justify combining simply due to mild rain.

And Allah knows best.',
         'Salah', 'en',
         '[{"collection": "Sahih Muslim", "hadithNumber": "705", "topic": "Prayer of Travelers and Concessions"}, {"surah": "An-Nisa", "ayah": "101", "text": "And when you travel throughout the land, there is no blame upon you for shortening the prayer"}]'::jsonb,
         'published', v_user_id, v_reviewer_id, now(), 'Approved after scholarly verification of weather and travel conditions.',
         v_user_id, now(), 1, now(), now()),

        (302, '30000000-0000-0000-0000-000000000002',
         'How should Zakat be calculated on modern financial assets, company shares, and retirement pension funds?',
         'Zakat is due on accessible wealth reaching the Nisab threshold.',
         'Praise be to Allah.

Zakat is the third pillar of Islam and is payable on lawful wealth that meets the minimum threshold (Nisab) and remains in one''s ownership for a full lunar year (Hawl).

### 1. Liquid Cash and Bank Savings
All personal liquid funds, savings accounts, and accessible cash are subject to a flat 2.5% rate annually, provided the total net wealth equals or exceeds the Nisab (the cash equivalent of 85 grams of pure gold).

### 2. Company Stocks and Equities
- **Short-Term Trading (Speculation):** If shares were acquired with the intention of frequent resale for trading gains, Zakat of 2.5% is due on the full market value of the portfolio at the end of the Zakat year.
- **Long-Term Investment:** If shares were bought for annual dividends without intent to liquidate, Zakat is due on the company''s underlying zakatable current assets (cash and receivables), which typically equates to approximately 25-30% of market value, or 2.5% on the dividends received.

### 3. Retirement & Pension Accounts (401k, IRA, Provident Funds)
- If the contributor has full, penalty-free access to withdraw the balance, Zakat is paid annually on the accessible amount after subtracting anticipated mandatory early withdrawal taxes and statutory penalties.
- If the funds are legally locked and inaccessible until retirement age, the majority of contemporary jurists (including the International Islamic Fiqh Academy) rule that no annual Zakat is required while the money is locked; rather, Zakat is paid once upon receiving the payout for one year.

And Allah knows best.',
         'Zakat', 'en',
         '[{"collection": "Sahih al-Bukhari", "hadithNumber": "1454", "topic": "Obligation of Zakat"}, {"surah": "At-Tawbah", "ayah": "103", "text": "Take from their wealth a charity by which you purify them and cause them increase"}]'::jsonb,
         'published', v_user_id, v_reviewer_id, now(), 'Conforms to standards established by the International Islamic Fiqh Academy.',
         v_user_id, now(), 1, now(), now()),

        (303, '30000000-0000-0000-0000-000000000003',
         'What are the rulings on fasting during Ramadan for pregnant women or those suffering from chronic medical conditions?',
         'Exemption applies with clear distinction between temporary and permanent inability.',
         'Praise be to Allah.

Allah has made the religion easy and free of undue distress. In Surah Al-Baqarah (2:185), Allah explicitly declares: *"Allah intends for you ease and does not intend for you hardship."*

### 1. Chronic or Permanent Illness
Individuals who suffer from serious chronic ailments (such as advanced diabetes requiring strict insulin intervals, severe kidney failure, or terminal conditions) where fasting would cause genuine medical harm, are permanently exempt upon the diagnosis of an honest medical physician.
- **Obligation:** Such individuals do not make up the fast days (Qada); instead, they pay *Fidyah* — feeding one poor person for each day missed (estimated at approximately 1.5 kg of staple food or its cash equivalent per day).

### 2. Temporary Illness
Those who suffer from a temporary curable illness (such as severe infection, acute fever, or surgical recovery) break their fast and must make up the missed days day-for-day after Ramadan once they recover. No Fidyah is required.

### 3. Pregnancy and Breastfeeding
If a pregnant or nursing mother genuinely fears for her own physical health or the health and development of her unborn child or infant, she is permitted to break her fast.
- According to the majority of scholars, she makes up the missed days (Qada) when able without paying Fidyah.
- If she feared specifically for the baby alone, some scholars also require Fidyah in addition to Qada.

And Allah knows best.',
         'Fasting', 'en',
         '[{"surah": "Al-Baqarah", "ayah": "184-185", "text": "And upon those who are able [to fast, but with hardship] - a ransom [as substitute] of feeding a poor person... and whoever is ill or on a journey - then an equal number of other days."}]'::jsonb,
         'published', v_user_id, v_reviewer_id, now(), 'Verified according to classical juristic consensus and contemporary medical ethics.',
         v_user_id, now(), 1, now(), now()),

        (304, '30000000-0000-0000-0000-000000000004',
         'What is the Islamic ruling regarding trading and investing in cryptocurrencies and digital tokens?',
         'Requires strict scrutiny regarding intrinsic utility, speculative volatility, and regulatory legality.',
         'Praise be to Allah.

The Islamic legal assessment of cryptocurrencies (such as Bitcoin, Ethereum, and utility tokens) is a subject of active research among contemporary Shariah advisory boards, including the AAOIFI, the International Islamic Fiqh Academy, and national Fatwa councils.

### Essential Shariah Determinants for Currencies and Assets:
1. **Mal Mutaqawwam (Legitimate Recognised Wealth):** The asset must possess real customary utility and benefit that the Shariah acknowledges.
2. **Freedom from Excessive Uncertainty (Gharar Fahish):** Contracts dominated by pure speculation where value fluctuates wildly without economic substance resemble gambling (Maysir).
3. **Absence of Usurious Schemes (Riba):** Yield farming, algorithmic leverage, and synthetic derivatives that generate guaranteed interest-like returns on loans are strictly prohibited.

### Current Scholarly Perspectives:
- **Strict Prohibition / Caution:** A notable group of fatwa councils consider speculative crypto assets impermissible due to high anonymity facilitating unlawful trade, extreme volatility, and lack of sovereign backing or intrinsic economic productivity.
- **Permissibility with Conditions:** Other contemporary bodies consider tokens permissible if they function as genuine utility tokens or decentralized payment networks, provided the underlying project is halal, compliant with financial laws, and not utilized for leveraged speculation or interest-bearing staking.

Muslim investors are advised to exercise extreme caution and seek specific guidance regarding individual project whitepapers.

And Allah knows best.',
         'Islamic Finance', 'en',
         '[{"collection": "Sahih Muslim", "hadithNumber": "1513", "topic": "Prohibition of Gharar (Uncertainty) and Hasat transactions"}]'::jsonb,
         'published', v_user_id, v_reviewer_id, now(), 'Reviewed against AAOIFI and International Fiqh Academy guidelines on digital assets.',
         v_user_id, now(), 1, now(), now()),

        (305, '30000000-0000-0000-0000-000000000005',
         'What are the obligations regarding upholding family ties (Silat ar-Rahim) when relatives are hostile or unreciprocal?',
         'Maintaining ties is a religious duty not predicated upon reciprocal kindness.',
         'Praise be to Allah.

Upholding kinship ties (Silat ar-Rahim) is among the weightiest obligations in Islamic character. Allah has linked maintaining family ties with divine mercy, and severed relations with divine displeasure.

### 1. The True Definition of Silat ar-Rahim
The Messenger of Allah ﷺ explicitly clarified that upholding ties is not merely reciprocating kindness when relatives treat you well. In *Sahih al-Bukhari* (5987), the Prophet ﷺ said:
*"The one who maintains ties of kinship is not the one who merely reciprocates. Rather, the one who maintains ties is the one who, when kinship ties are severed, connects them."*

### 2. Dealing with Hostile or Strained Relatives
A man came to the Prophet ﷺ and said: *"O Messenger of Allah, I have relatives with whom I try to maintain ties, but they cut me off; I treat them with kindness, but they treat me harshly; I am gentle with them, but they are hostile toward me."*
The Prophet ﷺ replied: *"If you are as you say, it is as though you are feeding them hot ashes, and Allah will remain your supporter against them as long as you continue upon this."* (Sahih Muslim, 2558).

### 3. Practical Guidelines for Protection
- One must maintain at least the baseline of connection: greeting them on Eid, inquiring after their health during illness, offering condolences in bereavement, and preventing malice.
- If close physical proximity leads to verified emotional or physical abuse, one may maintain a respectful boundary while preserving courteous communication and avoiding severed relations (Qati''at ar-Rahim).

And Allah knows best.',
         'Family', 'en',
         '[{"collection": "Sahih al-Bukhari", "hadithNumber": "5987", "topic": "The Upholder of Ties is Not Merely the Reciprocator"}, {"collection": "Sahih Muslim", "hadithNumber": "2558", "topic": "Excellence of Upholding Ties Even When Harmed"}]'::jsonb,
         'published', v_user_id, v_reviewer_id, now(), 'Approved by the Editorial Scholarly Committee.',
         v_user_id, now(), 1, now(), now())
    ON CONFLICT (public_id) DO UPDATE SET
        question = EXCLUDED.question,
        answer = EXCLUDED.answer,
        category = EXCLUDED.category,
        source_refs = EXCLUDED.source_refs,
        state = EXCLUDED.state,
        decision_notes = EXCLUDED.decision_notes;

    -- 7. CONTENT REVIEW RECORDS ----------------------------------------------
    INSERT INTO content_review_records (entity_type, entity_id, stage, reviewer_id, decision, notes, decided_at)
    VALUES
        ('fatwa', 301, 'scholarly_review', v_reviewer_id, 'approved', 'Verified weather and travel concessions against authentic Sunnah.', now()),
        ('fatwa', 302, 'scholarly_review', v_reviewer_id, 'approved', 'Conforms to AAOIFI and International Fiqh Academy standards.', now()),
        ('fatwa', 303, 'scholarly_review', v_reviewer_id, 'approved', 'Verified medical and juristic criteria for fasting exceptions.', now()),
        ('fatwa', 304, 'scholarly_review', v_reviewer_id, 'approved', 'Strict criteria for digital contracts verified.', now()),
        ('fatwa', 305, 'scholarly_review', v_reviewer_id, 'approved', 'Ethical obligations of kinship confirmed from Sahih Bukhari and Muslim.', now())
    ON CONFLICT DO NOTHING;

END $$;

-- 8. SYNC SEQUENCES FOR NEW INSERTIONS ---------------------------------------
SELECT setval(pg_get_serial_sequence('scholars', 'id'), coalesce((SELECT max(id) FROM scholars), 1));
SELECT setval(pg_get_serial_sequence('research_articles', 'id'), coalesce((SELECT max(id) FROM research_articles), 1));
SELECT setval(pg_get_serial_sequence('fatwas', 'id'), coalesce((SELECT max(id) FROM fatwas), 1));
SELECT setval(pg_get_serial_sequence('research_article_citations', 'id'), coalesce((SELECT max(id) FROM research_article_citations), 1));

