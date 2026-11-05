package com.ikp.dev.app.ui.quran;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Quran reader (M4 §7): Uthmani text with optional translation/tafsir
 * selection, word-by-word mode, per-ayah bookmark/copy/share, last-read
 * progress. Fixture content (licensed translation pending) is labelled. Audio
 * is an honest disabled state — no licensed recitation is imported yet.
 */
public class QuranReaderFragment extends Fragment {

    private final List<ContentDtos.AyahDto> ayahs = new ArrayList<>();
    private AyahAdapter adapter;
    private int surahId = 1;
    private int page = 1;
    private boolean loading = false;
    private boolean endReached = false;
    private String translationCode;
    private TextView stateText;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;

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
        SharedPreferences prefs = ServiceLocator.appPrefs();
        translationCode = prefs.getString("quran_translation_code", "dev-trans-en");
        stateText = v.findViewById(R.id.state_text);
        recycler = v.findViewById(R.id.recycler);
        adapter = new AyahAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        v.findViewById(R.id.btn_translation).setOnClickListener(x -> chooseEdition());
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
        audio.setText(R.string.quran_audio_unavailable);
        load();
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

    private void load() {
        if (loading || endReached) return;
        loading = true;
        stateText.setVisibility(ayahs.isEmpty() ? View.VISIBLE : View.GONE);
        if (ayahs.isEmpty()) stateText.setText(R.string.state_loading);
        final int pageParam = page;
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.AyahDto>> r = ServiceLocator.contentRepository()
                    .ayahs(surahId, translationCode, null, true, pageParam, 20);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
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
            h.translation.setVisibility(tr.length() == 0 ? View.GONE : View.VISIBLE);
            h.translation.setText(tr.toString());
            h.fixture.setVisibility(fixture ? View.VISIBLE : View.GONE);
            h.words.setVisibility(a.words == null || a.words.isEmpty() ? View.GONE : View.VISIBLE);
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

        AyahHolder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.tv_ayah_number);
            arabic = itemView.findViewById(R.id.tv_ayah_arabic);
            translation = itemView.findViewById(R.id.tv_ayah_translation);
            fixture = itemView.findViewById(R.id.tv_ayah_fixture);
            words = itemView.findViewById(R.id.tv_ayah_words);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnCopy = itemView.findViewById(R.id.btn_copy);
        }
    }
}
