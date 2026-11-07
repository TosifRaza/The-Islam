package com.ikp.dev.app.ui.quran;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.data.remote.QuranAudioSource;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quran reader (M4 §7): Uthmani text with optional translation/tafsir
 * selection, word-by-word mode, per-ayah bookmark/copy/share, last-read
 * progress. Fixture content (licensed translation pending) is labelled. Verse
 * recitations stream on demand from Al Quran Cloud; the mini-player is shown
 * only after a verse is selected for playback.
 */
public class QuranReaderFragment extends Fragment {

    private final List<ContentDtos.AyahDto> ayahs = new ArrayList<>();
    private AyahAdapter adapter;
    private int surahId = 1;
    private int page = 1;
    private boolean loading = false;
    private boolean endReached = false;
    private int contentRequestGeneration = 0;
    private String translationCode;
    private String tafsirCode;
    private ReaderTab readerTab = ReaderTab.TRANSLATION;
    private TextView arabicTab;
    private TextView translationTab;
    private TextView tafsirTab;
    private TextView stateText;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private View bottomPlayer;
    private View playerPlayButton;
    private View playerPreviousButton;
    private View playerNextButton;
    private View playSurahButton;
    private TextView playerTitle;
    private TextView playerSubtitle;
    private TextView playerElapsed;
    private TextView playerDuration;
    private TextView playerPlayIcon;
    private TextView surahPlayIcon;
    private TextView surahNameHeader;
    private TextView surahSubtitleHeader;
    private TextView surahMetaHeader;
    private TextView surahNumberBadge;
    private ProgressBar playerProgress;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private int audioRequestGeneration = 0;
    private int currentAudioSurah = 0;
    private int currentAudioAyah = 0;
    private int currentSurahAyahCount = 0;
    private String currentSurahDisplayName;
    private boolean fullSurahAudio = false;
    private boolean audioPrepared = false;
    private boolean audioPlaying = false;
    private boolean audioPreparing = false;

    private final Runnable audioProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer == null || !audioPrepared || !audioPlaying || playerProgress == null) {
                return;
            }
            try {
                int duration = mediaPlayer.getDuration();
                int position = mediaPlayer.getCurrentPosition();
                playerProgress.setMax(Math.max(1, duration));
                playerProgress.setProgress(position);
                playerElapsed.setText(formatTime(position));
                playerDuration.setText(formatTime(duration));
                mainHandler.postDelayed(this, 500);
            } catch (IllegalStateException ignored) {
                // The player may be released while a progress update is queued.
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quran_reader, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) surahId = args.getInt("surahId", 1);
        currentSurahDisplayName = getString(R.string.quran_audio_surah_generic, surahId);
        SharedPreferences prefs = ServiceLocator.appPrefs();
        translationCode = prefs.getString("quran_translation_code", "dev-trans-en");
        stateText = v.findViewById(R.id.state_text);
        recycler = v.findViewById(R.id.recycler);
        bottomPlayer = v.findViewById(R.id.layout_bottom_player);
        playerPlayButton = v.findViewById(R.id.btn_player_play);
        playerPreviousButton = v.findViewById(R.id.btn_player_previous);
        playerNextButton = v.findViewById(R.id.btn_player_next);
        playerTitle = v.findViewById(R.id.tv_player_surah_name);
        playerSubtitle = v.findViewById(R.id.tv_player_sub_name);
        playerElapsed = v.findViewById(R.id.tv_player_elapsed);
        playerDuration = v.findViewById(R.id.tv_player_duration);
        playerPlayIcon = v.findViewById(R.id.tv_player_play_icon);
        playerProgress = v.findViewById(R.id.player_progress);
        playSurahButton = v.findViewById(R.id.btn_play_surah);
        surahPlayIcon = v.findViewById(R.id.tv_surah_play_icon);
        surahNameHeader = v.findViewById(R.id.tv_surah_name_header);
        surahSubtitleHeader = v.findViewById(R.id.tv_surah_sub_header);
        surahMetaHeader = v.findViewById(R.id.tv_surah_meta_header);
        surahNumberBadge = v.findViewById(R.id.tv_surah_number_badge);
        bottomPlayer.setVisibility(View.GONE);
        playerPlayButton.setOnClickListener(x -> toggleAudioPlayback());
        playerPreviousButton.setOnClickListener(x -> playAdjacentAyah(-1));
        playerNextButton.setOnClickListener(x -> playAdjacentAyah(1));
        playSurahButton.setOnClickListener(x -> startFullSurahAudio(surahId));
        adapter = new AyahAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        arabicTab = v.findViewById(R.id.tab_arabic);
        translationTab = v.findViewById(R.id.btn_translation);
        tafsirTab = v.findViewById(R.id.tab_tafsir);
        arabicTab.setOnClickListener(x -> selectTab(ReaderTab.ARABIC));
        translationTab.setOnClickListener(x -> {
            selectTab(ReaderTab.TRANSLATION);
            chooseEdition();
        });
        tafsirTab.setOnClickListener(x -> {
            selectTab(ReaderTab.TAFSIR);
            chooseTafsirEdition();
        });
        updateTabStyles();
        v.findViewById(R.id.btn_retry).setOnClickListener(x -> {
            page = 1;
            endReached = false;
            ayahs.clear();
            load();
        });
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!loading && !endReached && layoutManager != null
                        && layoutManager.findLastVisibleItemPosition() >= ayahs.size() - 3) {
                    load();
                }
            }
        });
        TextView audio = v.findViewById(R.id.audio_note);
        audio.setText(R.string.quran_audio_attribution);
        loadCurrentSurahAyahCount();
        load();
    }

    private void loadCurrentSurahAyahCount() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.SurahDto>> result =
                    ServiceLocator.contentRepository().surahs(false);
            if (!isAdded() || !result.isSuccess() || result.value == null) return;
            ContentDtos.SurahDto selectedSurah = null;
            for (ContentDtos.SurahDto surah : result.value) {
                if (surah.id == surahId) {
                    selectedSurah = surah;
                    break;
                }
            }
            if (selectedSurah == null) return;
            final int resolvedCount = selectedSurah.ayahCount;
            final String resolvedName = selectedSurah.nameTransliterated == null
                    || selectedSurah.nameTransliterated.isBlank()
                    ? getString(R.string.quran_audio_surah_generic, surahId)
                    : selectedSurah.nameTransliterated;
            final String resolvedEnglishName = selectedSurah.nameEn == null
                    ? "" : selectedSurah.nameEn;
            final String resolvedPlace = selectedSurah.revelationPlace == null
                    ? "" : selectedSurah.revelationPlace;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                currentSurahAyahCount = resolvedCount;
                currentSurahDisplayName = resolvedName;
                surahNameHeader.setText(resolvedName);
                surahSubtitleHeader.setText(resolvedEnglishName);
                surahMetaHeader.setText(getString(
                        R.string.quran_surah_meta, resolvedPlace, resolvedCount));
                surahNumberBadge.setText(String.valueOf(surahId));
                updateAudioNavigationControls();
            });
        });
    }

    private void startAyahAudio(int selectedSurah, int selectedAyah) {
        if (selectedSurah == currentAudioSurah && selectedAyah == currentAudioAyah
                && mediaPlayer != null && audioPrepared) {
            toggleAudioPlayback();
            return;
        }

        final int request = ++audioRequestGeneration;
        releaseMediaPlayer();
        currentAudioSurah = selectedSurah;
        currentAudioAyah = selectedAyah;
        fullSurahAudio = false;
        audioPreparing = true;
        bottomPlayer.setVisibility(View.VISIBLE);
        playerTitle.setText(getString(R.string.quran_audio_track, selectedSurah, selectedAyah));
        playerSubtitle.setText(R.string.quran_audio_loading);
        playerElapsed.setText("0:00");
        playerDuration.setText("--:--");
        playerProgress.setIndeterminate(true);
        playerProgress.setProgress(0);
        playerPlayButton.setEnabled(false);
        playerPlayIcon.setText("…");
        playerPlayButton.setContentDescription(getString(R.string.quran_audio_loading));
        updateAudioNavigationControls();

        ServiceLocator.executors().io.execute(() -> {
            try {
                String audioUrl = QuranAudioSource.resolveAyahUrl(selectedSurah, selectedAyah);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (request == audioRequestGeneration && bottomPlayer != null) {
                        prepareAndPlayAudio(audioUrl, request);
                    }
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> failAudioRequest(request));
            }
        });
    }

    private void startFullSurahAudio(int selectedSurah) {
        if (fullSurahAudio && selectedSurah == currentAudioSurah
                && mediaPlayer != null && audioPrepared) {
            toggleAudioPlayback();
            return;
        }

        final int request = ++audioRequestGeneration;
        releaseMediaPlayer();
        currentAudioSurah = selectedSurah;
        currentAudioAyah = 0;
        fullSurahAudio = true;
        audioPreparing = true;
        bottomPlayer.setVisibility(View.VISIBLE);
        playerTitle.setText(getString(R.string.quran_audio_full_surah_track,
                currentSurahDisplayName));
        playerSubtitle.setText(R.string.quran_audio_loading);
        playerElapsed.setText("0:00");
        playerDuration.setText("--:--");
        playerProgress.setIndeterminate(true);
        playerProgress.setProgress(0);
        playerPlayButton.setEnabled(false);
        playerPlayIcon.setText("…");
        playerPlayButton.setContentDescription(getString(R.string.quran_audio_loading));
        updateAudioNavigationControls();
        playSurahButton.setContentDescription(getString(R.string.quran_audio_loading));

        final String audioUrl;
        try {
            audioUrl = QuranAudioSource.surahAudioUrl(selectedSurah);
        } catch (IllegalArgumentException e) {
            failAudioRequest(request);
            return;
        }
        mainHandler.post(() -> {
            if (request == audioRequestGeneration && bottomPlayer != null) {
                prepareAndPlayAudio(audioUrl, request);
            }
        });
    }

    private void prepareAndPlayAudio(String audioUrl, int request) {
        MediaPlayer player = new MediaPlayer();
        mediaPlayer = player;
        audioPreparing = true;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        player.setOnPreparedListener(preparedPlayer -> {
            if (request != audioRequestGeneration || bottomPlayer == null) {
                preparedPlayer.release();
                return;
            }
            audioPreparing = false;
            audioPrepared = true;
            audioPlaying = true;
            playerSubtitle.setText(R.string.quran_audio_reciter);
            playerProgress.setIndeterminate(false);
            playerProgress.setMax(Math.max(1, preparedPlayer.getDuration()));
            playerProgress.setProgress(0);
            playerElapsed.setText("0:00");
            playerDuration.setText(formatTime(preparedPlayer.getDuration()));
            playerPlayButton.setEnabled(true);
            updateAudioPlayButton();
            preparedPlayer.start();
            mainHandler.removeCallbacks(audioProgressUpdater);
            mainHandler.post(audioProgressUpdater);
        });
        player.setOnCompletionListener(completedPlayer -> {
            if (request == audioRequestGeneration) hideAudioPlayer();
        });
        player.setOnErrorListener((failedPlayer, what, extra) -> {
            if (request == audioRequestGeneration) failAudioRequest(request);
            return true;
        });
        try {
            player.setDataSource(audioUrl);
            player.prepareAsync();
        } catch (Exception e) {
            failAudioRequest(request);
        }
    }

    private void toggleAudioPlayback() {
        if (mediaPlayer == null || !audioPrepared || audioPreparing) return;
        try {
            if (audioPlaying) {
                mediaPlayer.pause();
                audioPlaying = false;
                mainHandler.removeCallbacks(audioProgressUpdater);
            } else {
                mediaPlayer.start();
                audioPlaying = true;
                mainHandler.post(audioProgressUpdater);
            }
            updateAudioPlayButton();
        } catch (IllegalStateException e) {
            failAudioRequest(audioRequestGeneration);
        }
    }

    private void playAdjacentAyah(int offset) {
        if (fullSurahAudio || currentAudioAyah == 0) return;
        int targetAyah = currentAudioAyah + offset;
        if (targetAyah < 1 || targetAyah > currentSurahAyahCount) return;
        startAyahAudio(currentAudioSurah, targetAyah);
    }

    private void updateAudioNavigationControls() {
        if (playerPreviousButton == null || playerNextButton == null) return;
        int visibility = fullSurahAudio ? View.GONE : View.VISIBLE;
        playerPreviousButton.setVisibility(visibility);
        playerNextButton.setVisibility(visibility);
        playerPreviousButton.setEnabled(currentAudioAyah > 1);
        playerNextButton.setEnabled(currentSurahAyahCount > 0
                && currentAudioAyah > 0 && currentAudioAyah < currentSurahAyahCount);
    }

    private void updateAudioPlayButton() {
        if (playerPlayIcon == null || playerPlayButton == null) return;
        playerPlayIcon.setText(audioPlaying ? "Ⅱ" : "▶");
        playerPlayButton.setContentDescription(getString(audioPlaying
                ? R.string.quran_audio_pause : R.string.quran_audio_resume));
        if (surahPlayIcon != null) {
            surahPlayIcon.setText(fullSurahAudio && audioPlaying ? "Ⅱ" : "▶");
        }
        if (playSurahButton != null) {
            playSurahButton.setContentDescription(getString(fullSurahAudio && audioPlaying
                    ? R.string.quran_audio_pause_surah_cd : R.string.quran_audio_play_surah_cd));
        }
    }

    private void failAudioRequest(int request) {
        if (request != audioRequestGeneration) return;
        hideAudioPlayer();
        if (isAdded()) {
            Toast.makeText(requireContext(), R.string.quran_audio_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideAudioPlayer() {
        audioRequestGeneration++;
        mainHandler.removeCallbacks(audioProgressUpdater);
        releaseMediaPlayer();
        currentAudioSurah = 0;
        currentAudioAyah = 0;
        fullSurahAudio = false;
        if (bottomPlayer != null) bottomPlayer.setVisibility(View.GONE);
        updateAudioNavigationControls();
        if (surahPlayIcon != null) surahPlayIcon.setText("▶");
        if (playSurahButton != null) {
            playSurahButton.setContentDescription(getString(R.string.quran_audio_play_surah_cd));
        }
    }

    private void releaseMediaPlayer() {
        MediaPlayer oldPlayer = mediaPlayer;
        mediaPlayer = null;
        audioPrepared = false;
        audioPlaying = false;
        audioPreparing = false;
        if (oldPlayer == null) return;
        oldPlayer.setOnPreparedListener(null);
        oldPlayer.setOnCompletionListener(null);
        oldPlayer.setOnErrorListener(null);
        try {
            oldPlayer.stop();
        } catch (IllegalStateException ignored) {
            // Stopping a player that has not completed preparation is invalid.
        }
        oldPlayer.release();
    }

    private static String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void onDestroyView() {
        audioRequestGeneration++;
        mainHandler.removeCallbacks(audioProgressUpdater);
        releaseMediaPlayer();
        bottomPlayer = null;
        playerPlayButton = null;
        playerPreviousButton = null;
        playerNextButton = null;
        playSurahButton = null;
        playerTitle = null;
        playerSubtitle = null;
        playerElapsed = null;
        playerDuration = null;
        playerPlayIcon = null;
        playerProgress = null;
        surahPlayIcon = null;
        surahNameHeader = null;
        surahSubtitleHeader = null;
        surahMetaHeader = null;
        surahNumberBadge = null;
        arabicTab = null;
        translationTab = null;
        tafsirTab = null;
        super.onDestroyView();
    }

    private void chooseEdition() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.TranslationEditionDto>> r =
                    ServiceLocator.contentRepository().translationEditions();
            if (!isAdded()) return;
            if (r.isSuccess() && r.value != null) {
                List<String> codes = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (ContentDtos.TranslationEditionDto e : r.value) {
                    codes.add(e.code);
                    labels.add(e.displayName
                            + (Boolean.TRUE.equals(e.isFixture)
                               ? " [" + getString(R.string.fixture_badge) + "]" : ""));
                }
                requireActivity().runOnUiThread(() -> new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.quran_choose_translation)
                        .setItems(labels.toArray(new String[0]), (d, w) -> {
                            translationCode = codes.get(w);
                            ServiceLocator.appPrefs().edit()
                                    .putString("quran_translation_code", translationCode).apply();
                            page = 1;
                            endReached = false;
                            ayahs.clear();
                            load();
                        }).show());
            }
        });
    }

    private void selectTab(ReaderTab tab) {
        if (readerTab == tab) return;
        readerTab = tab;
        updateTabStyles();
        page = 1;
        endReached = false;
        loading = false;
        ayahs.clear();
        adapter.notifyDataSetChanged();
        load();
    }

    private void updateTabStyles() {
        if (arabicTab == null || translationTab == null || tafsirTab == null) return;
        arabicTab.setBackgroundResource(readerTab == ReaderTab.ARABIC
                ? R.drawable.bg_pill_gold_active : R.drawable.bg_pill_dark_tab);
        translationTab.setBackgroundResource(readerTab == ReaderTab.TRANSLATION
                ? R.drawable.bg_pill_gold_active : R.drawable.bg_pill_dark_tab);
        tafsirTab.setBackgroundResource(readerTab == ReaderTab.TAFSIR
                ? R.drawable.bg_pill_gold_active : R.drawable.bg_pill_dark_tab);
    }

    private void chooseTafsirEdition() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.TafsirEditionDto>> result =
                    ServiceLocator.contentRepository().tafsirEditions();
            if (!isAdded()) return;
            if (!result.isSuccess() || result.value == null || result.value.isEmpty()) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(),
                        "No tafsir editions are available right now", Toast.LENGTH_SHORT).show());
                return;
            }
            List<String> codes = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            int selected = 0;
            for (ContentDtos.TafsirEditionDto edition : result.value) {
                codes.add(edition.code);
                String label = edition.nameEn == null ? edition.code : edition.nameEn;
                if (edition.author != null && !edition.author.isBlank()) label += " — " + edition.author;
                labels.add(label + (Boolean.TRUE.equals(edition.isFixture)
                        ? " [" + getString(R.string.fixture_badge) + "]" : ""));
                if (edition.code != null && edition.code.equals(tafsirCode)) selected = codes.size() - 1;
            }
            final int selectedIndex = selected;
            requireActivity().runOnUiThread(() -> new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Choose Tafsir")
                    .setSingleChoiceItems(labels.toArray(new String[0]), selectedIndex, (dialog, which) -> {
                        tafsirCode = codes.get(which);
                        page = 1;
                        endReached = false;
                        loading = false;
                        ayahs.clear();
                        adapter.notifyDataSetChanged();
                        dialog.dismiss();
                        load();
                    }).setNegativeButton(android.R.string.cancel, null).show());
        });
    }

    private void load() {
        if (loading || endReached) return;
        loading = true;
        if (getView() != null) {
            View retry = getView().findViewById(R.id.btn_retry);
            if (retry != null) retry.setVisibility(View.GONE);
        }
        stateText.setVisibility(ayahs.isEmpty() ? View.VISIBLE : View.GONE);
        if (ayahs.isEmpty()) stateText.setText(R.string.state_loading);
        final int pageParam = page;
        final int requestGeneration = ++contentRequestGeneration;
        final ReaderTab requestedTab = readerTab;
        final String requestedTranslation = readerTab == ReaderTab.TRANSLATION ? translationCode : null;
        final String requestedTafsir = readerTab == ReaderTab.TAFSIR ? tafsirCode : null;
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.AyahDto>> r = ServiceLocator.contentRepository()
                    .ayahs(surahId, requestedTranslation, requestedTafsir, true, pageParam, 20);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (requestGeneration != contentRequestGeneration || requestedTab != readerTab) return;
                loading = false;
                if (r.isSuccess() && r.value != null) {
                    if (r.value.size() < 20) endReached = true;
                    ayahs.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                    page++;
                    if (pageParam == 1) saveProgress(1);
                } else if (ayahs.isEmpty()) {
                    stateText.setText(R.string.state_error_offline);
                    v2ShowRetry();
                }
            });
        });
    }

    private void v2ShowRetry() {
        if (getView() != null) getView().findViewById(R.id.btn_retry).setVisibility(View.VISIBLE);
    }

    private void saveProgress(int ayahNumber) {
        ServiceLocator.executors().io.execute(() -> {
            int percent = Math.min(99, ayahNumber);
            ServiceLocator.userContentRepository()
                    .putProgress("ayah:" + surahId + ":" + ayahNumber, percent, "{}");
        });
    }

    private final class AyahAdapter extends RecyclerView.Adapter<AyahHolder> {

        @NonNull
        @Override
        public AyahHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AyahHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ayah, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AyahHolder h, int position) {
            ContentDtos.AyahDto a = ayahs.get(position);
            h.number.setText(getString(R.string.quran_ayah_number, a.surahId, a.ayahNumber));
            h.arabic.setText(a.textUthmani);
            h.btnPlay.setContentDescription(getString(R.string.quran_audio_play_ayah_cd, a.ayahNumber));
            h.btnPlay.setOnClickListener(x -> startAyahAudio(a.surahId, a.ayahNumber));
            StringBuilder tr = new StringBuilder();
            boolean fixture = Boolean.TRUE.equals(a.fixtureContent);
            if (a.translation != null) {
                for (ContentDtos.TranslationRowDto t : a.translation) {
                    if (t.text != null) {
                        tr.append(t.text).append('\n');
                        if (Boolean.TRUE.equals(t.isFixture)) fixture = true;
                    }
                }
            }
            String displayedText = tr.toString();
            if (readerTab == ReaderTab.TAFSIR) {
                displayedText = a.tafsir == null || a.tafsir.text == null ? "" : a.tafsir.text;
                if (a.tafsir != null && Boolean.TRUE.equals(a.tafsir.isFixture)) fixture = true;
            }
            h.translation.setVisibility(readerTab == ReaderTab.ARABIC || displayedText.isEmpty()
                    ? View.GONE : View.VISIBLE);
            h.translation.setText(displayedText);
            h.fixture.setVisibility(fixture ? View.VISIBLE : View.GONE);
            h.words.setVisibility(readerTab != ReaderTab.TRANSLATION
                    || a.words == null || a.words.isEmpty() ? View.GONE : View.VISIBLE);
            if (a.words != null) {
                StringBuilder wb = new StringBuilder();
                for (ContentDtos.WordDto w : a.words) {
                    wb.append(w.textAr);
                    if (w.meaningEn != null) wb.append(" — ").append(w.meaningEn);
                    wb.append('\n');
                }
                h.words.setText(wb.toString());
            }
            h.btnBookmark.setOnClickListener(x -> ServiceLocator.executors().io.execute(() ->
                    ServiceLocator.userContentRepository()
                            .putBookmark("ayah:" + a.surahId + ":" + a.ayahNumber, null)));
            h.btnShare.setOnClickListener(x -> {
                String text = a.textUthmani + "\n— Quran " + a.surahId + ":" + a.ayahNumber
                        + " · Tanzil Uthmani v1.1 (CC BY 3.0)";
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, getString(R.string.action_share)));
            });
            h.btnCopy.setOnClickListener(x -> {
                ClipboardManager cm = (ClipboardManager)
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("ayah", a.textUthmani
                        + "\n— Quran " + a.surahId + ":" + a.ayahNumber));
                Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return ayahs.size();
        }
    }

    private static final class AyahHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView arabic;
        final TextView translation;
        final TextView fixture;
        final TextView words;
        final View btnBookmark;
        final View btnShare;
        final View btnCopy;
        final View btnPlay;

        AyahHolder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.tv_ayah_number);
            arabic = itemView.findViewById(R.id.tv_ayah_arabic);
            translation = itemView.findViewById(R.id.tv_ayah_translation);
            fixture = itemView.findViewById(R.id.tv_ayah_fixture);
            words = itemView.findViewById(R.id.tv_ayah_words);
            btnPlay = itemView.findViewById(R.id.btn_play_ayah);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }

    private enum ReaderTab { ARABIC, TRANSLATION, TAFSIR }
}
