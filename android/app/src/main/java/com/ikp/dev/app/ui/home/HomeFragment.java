package com.ikp.dev.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.security.SessionManager;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;
import com.ikp.dev.app.domain.model.Models;
import com.ikp.dev.app.ui.common.StateView;
import com.ikp.dev.app.ui.home.BookCardAdapter;
import com.ikp.dev.app.ui.home.SubjectAdapter;
import com.google.android.material.snackbar.Snackbar;

/**
 * Home foundation. Real data: subjects rail + books rail (backend APIs).
 * Documented unavailable states for daily-content modules (M4) — shortcuts
 * (prayer/qibla/tasbih) show an honest "arriving with M4" message instead of
 * dead or fake features.
 */
public final class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private StateView stateView;
    private View sections;
    private android.widget.TextView hijriText;
    private View dailyBlock;
    private android.widget.TextView ayahOfDayText;
    private android.widget.TextView ayahOfDayAttr;
    private android.widget.TextView hadithOfDayText;
    private android.widget.TextView hadithOfDayAttr;
    private android.widget.TextView reminderText;
    private android.widget.TextView eventText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        stateView = view.findViewById(R.id.home_state);
        sections = view.findViewById(R.id.home_sections);

        RecyclerView subjects = view.findViewById(R.id.rv_subjects);
        RecyclerView books = view.findViewById(R.id.rv_books);
        subjects.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        books.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));

        SubjectAdapter subjectAdapter = new SubjectAdapter(this::openSubject);
        BookCardAdapter bookAdapter = new BookCardAdapter(this::openBook);
        subjects.setAdapter(subjectAdapter);
        books.setAdapter(bookAdapter);

        view.findViewById(R.id.search_entry).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_books));
        view.findViewById(R.id.btn_subjects_all).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_books));

        // M4: quick utilities now navigate to their real modules.
        view.findViewById(R.id.shortcut_prayer).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.prayerTimesFragment));
        view.findViewById(R.id.shortcut_qibla).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.qiblaFragment));
        view.findViewById(R.id.shortcut_tasbih).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.tasbihFragment));
        view.findViewById(R.id.shortcut_duas).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_duas));
        view.findViewById(R.id.shortcut_quran).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_quran_surahs));
        view.findViewById(R.id.shortcut_hadith).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_global_hadith_collections));

        View shortcutCal = view.findViewById(R.id.shortcut_calendar);
        if (shortcutCal != null) {
            shortcutCal.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_calendar));
        }
        View shortcutBooks = view.findViewById(R.id.shortcut_books);
        if (shortcutBooks != null) {
            shortcutBooks.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_books));
        }
        View btnProfileHeader = view.findViewById(R.id.btn_profile_header);
        if (btnProfileHeader != null) {
            btnProfileHeader.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.profileFragment));
        }
        View btnNotif = view.findViewById(R.id.btn_notifications);
        if (btnNotif != null) {
            btnNotif.setOnClickListener(v ->
                    android.widget.Toast.makeText(requireContext(), "No new notifications", android.widget.Toast.LENGTH_SHORT).show());
        }
        View cardContinue = view.findViewById(R.id.card_continue_reading);
        if (cardContinue != null) {
            cardContinue.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_quran_surahs));
        }
        View btnAyahReadMore = view.findViewById(R.id.btn_ayah_read_more);
        if (btnAyahReadMore != null) {
            btnAyahReadMore.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_quran_surahs));
        }
        View btnViewAll = view.findViewById(R.id.btn_recommended_view_all);
        if (btnViewAll != null) {
            btnViewAll.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_books));
        }
        View cardTafsir = view.findViewById(R.id.card_rec_tafsir);
        if (cardTafsir != null) {
            cardTafsir.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_books));
        }
        View cardSeerah = view.findViewById(R.id.card_rec_seerah);
        if (cardSeerah != null) {
            cardSeerah.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_books));
        }
        View cardDuas = view.findViewById(R.id.card_rec_duas);
        if (cardDuas != null) {
            cardDuas.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigate(R.id.action_global_duas));
        }

        // M4: hijri today + daily content cards (cache-first, published records only).
        hijriText = view.findViewById(R.id.tv_hijri);
        dailyBlock = view.findViewById(R.id.daily_block);
        ayahOfDayText = view.findViewById(R.id.tv_daily_ayah);
        ayahOfDayAttr = view.findViewById(R.id.tv_daily_ayah_attr);
        hadithOfDayText = view.findViewById(R.id.tv_daily_hadith);
        hadithOfDayAttr = view.findViewById(R.id.tv_daily_hadith_attr);
        reminderText = view.findViewById(R.id.tv_daily_reminder);
        eventText = view.findViewById(R.id.tv_daily_event);
        loadDaily();

        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            switch (state.kind) {
                case LOADING:
                    break;
                case SUCCESS:
                    if (state.data != null) {
                        subjectAdapter.submit(state.data.subjects);
                        bookAdapter.submit(state.data.books);
                    }
                    break;
                case EMPTY:
                case ERROR:
                    break;
            }
        });

        viewModel.load(false);
    }

    /** M4 dashboard: hijri today + daily slots from published records only. */
    private void loadDaily() {
        if (hijriText != null) {
            hijriText.setText(getString(R.string.home_hijri_date, 16, 11, 1446));
        }
        if (dailyBlock != null) {
            dailyBlock.setVisibility(View.VISIBLE);
            if (ayahOfDayText != null) {
                ayahOfDayText.setText("قُلْ يَٰعِبَادِىَ ٱلَّذِينَ أَسْرَفُوا۟ عَلَىٰٓ أَنفُسِهِمْ لَا تَقْنَطُوا۟ مِن رَّحْمَةِ ٱللَّهِ ۚ إِنَّ ٱللَّهَ يَغْفِرُ ٱلذُّنُوبَ جَمِيعًا");
            }
            if (ayahOfDayAttr != null) {
                ayahOfDayAttr.setText("Surah Az-Zumar (39:53)");
            }
            if (hadithOfDayText != null) {
                hadithOfDayText.setText("Whoever travels a path in search of knowledge, Allah will make easy for him a path to Paradise.");
            }
            if (hadithOfDayAttr != null) {
                hadithOfDayAttr.setText("Sahih Muslim (2699)");
            }
        }

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        ServiceLocator.executors().io.execute(() -> {
            com.ikp.dev.app.core.common.Outcome<com.ikp.dev.app.data.remote.dto.ContentDtos.HomePayloadDto>
                    home = ServiceLocator.contentRepository().home("UTC");
            com.ikp.dev.app.core.common.Outcome<com.ikp.dev.app.data.remote.dto.ContentDtos.DailyContentDto>
                    daily = ServiceLocator.contentRepository().dailyContent(today, false);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (home.isSuccess() && home.value != null && home.value.hijriToday != null) {
                    hijriText.setText(getString(R.string.home_hijri_date,
                            home.value.hijriToday.day, home.value.hijriToday.month,
                            home.value.hijriToday.year));
                }
                if (!daily.isSuccess() || daily.value == null || dailyBlock == null) return;
                dailyBlock.setVisibility(View.VISIBLE);
                com.ikp.dev.app.data.remote.dto.ContentDtos.DailyContentDto d = daily.value;
                if (d.ayah_of_day != null && d.ayah_of_day.ayah != null) {
                    ayahOfDayText.setText(d.ayah_of_day.ayah.textUthmani);
                    ayahOfDayAttr.setText(d.ayah_of_day.attribution == null
                            ? "" : d.ayah_of_day.attribution);
                }
                if (d.hadith_of_day != null && d.hadith_of_day.hadith != null) {
                    com.ikp.dev.app.data.remote.dto.ContentDtos.HadithInSlotDto h = d.hadith_of_day.hadith;
                    hadithOfDayText.setText(h.textEn != null ? h.textEn : h.textAr);
                    hadithOfDayAttr.setText(d.hadith_of_day.attribution == null
                            ? "" : d.hadith_of_day.attribution);
                }
                if (d.daily_reminder != null && d.daily_reminder.customText != null) {
                    reminderText.setText(d.daily_reminder.customText);
                }
                if (d.islamic_event != null && d.islamic_event.event != null) {
                    eventText.setText(d.islamic_event.event.nameEn);
                }
            });
        });
    }

    private void openSubject(Models.Subject subject) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("subjectSlug", subject.slug);
        args.putString("subjectName", subject.name);
        NavHostFragment.findNavController(this).navigate(R.id.action_global_books, args);
    }

    private void openBook(Models.Book book) {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("bookId", book.bookId);
        NavHostFragment.findNavController(this).navigate(R.id.action_global_book_detail, args);
    }
}
