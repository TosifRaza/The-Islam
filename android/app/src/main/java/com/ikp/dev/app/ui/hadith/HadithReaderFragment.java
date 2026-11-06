package com.ikp.dev.app.ui.hadith;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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

import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Hadith list of one book (M4 §8): Arabic + available translations, gradings
 * WITH provenance (never merged), numbering note footer, bookmark/copy/share.
 */
public class HadithReaderFragment extends Fragment {

    private final List<ContentDtos.HadithDto> hadiths = new ArrayList<>();
    private HadithAdapter adapter;
    private long bookId;
    private int page = 1;
    private boolean loading;
    private boolean endReached;
    private TextView stateText;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private String numberingNote = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hadith_reader, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) bookId = args.getLong("bookId", 0);
        stateText = v.findViewById(R.id.state_text);
        recycler = v.findViewById(R.id.recycler);
        adapter = new HadithAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        View retryBtn = v.findViewById(R.id.btn_retry);
        if (retryBtn != null) {
            retryBtn.setVisibility(View.GONE);
            retryBtn.setOnClickListener(x -> {
                page = 1;
                endReached = false;
                hadiths.clear();
                load();
            });
        }
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!loading && !endReached && layoutManager != null
                        && layoutManager.findLastVisibleItemPosition() >= hadiths.size() - 3) {
                    load();
                }
            }
        });
        load();
    }

    private void load() {
        if (loading || endReached || bookId <= 0) return;
        loading = true;
        if (getView() != null) {
            View retry = getView().findViewById(R.id.btn_retry);
            if (retry != null) retry.setVisibility(View.GONE);
        }
        stateText.setVisibility(hadiths.isEmpty() ? View.VISIBLE : View.GONE);
        stateText.setText(R.string.state_loading);
        final int p = page;
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.HadithDto>> r =
                    ServiceLocator.contentRepository().hadithsOfBook(bookId, p, 20);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                loading = false;
                if (r.isSuccess() && r.value != null) {
                    if (r.value.size() < 20) endReached = true;
                    hadiths.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                    if (getView() != null) {
                        View retry = getView().findViewById(R.id.btn_retry);
                        if (retry != null) retry.setVisibility(View.GONE);
                    }
                    page++;
                    updateFooter();
                } else if (hadiths.isEmpty()) {
                    stateText.setText(R.string.state_error_offline);
                    if (getView() != null) getView().findViewById(R.id.btn_retry).setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void updateFooter() {
        if (getView() == null || numberingNote == null) return;
        TextView footer = getView().findViewById(R.id.tv_numbering_note);
        if (footer != null && !numberingNote.isEmpty()) {
            footer.setText(getString(R.string.hadith_numbering_note, numberingNote));
            footer.setVisibility(View.VISIBLE);
        }
    }

    private final class HadithAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hadith_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.HadithDto hd = hadiths.get(position);
            h.number.setText(getString(R.string.hadith_item_number, hd.numberInBook));
            StringBuilder body = new StringBuilder();
            if (hd.textAr != null) body.append(hd.textAr).append("\n\n");
            if (hd.textEn != null) body.append(hd.textEn).append("\n\n");
            if (hd.textUr != null) body.append(hd.textUr).append("\n\n");
            h.body.setText(body.toString().trim());
            if (hd.gradings == null || hd.gradings.isEmpty()) {
                h.gradings.setText(R.string.hadith_grading_not_recorded);
            } else {
                StringBuilder gb = new StringBuilder();
                for (ContentDtos.GradingDto g : hd.gradings) {
                    gb.append(getString(R.string.hadith_grading_line, g.gradedBy, g.grade, g.source))
                      .append('\n');
                }
                h.gradings.setText(gb.toString().trim());
            }
            h.gradings.setVisibility(View.VISIBLE);
            h.takhrij.setVisibility(hd.takhrij == null ? View.GONE : View.VISIBLE);
            h.takhrij.setText(hd.takhrij);
            h.btnBookmark.setOnClickListener(x -> ServiceLocator.executors().io.execute(() ->
                    ServiceLocator.userContentRepository().putBookmark("hadith:" + hd.id, null)));
            h.btnCopy.setOnClickListener(x -> {
                ClipboardManager cm = (ClipboardManager)
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("hadith",
                        body + "\n— " + hd.collectionName + " " + hd.numberInBook));
                Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show();
            });
            h.btnShare.setOnClickListener(x -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, body + "\n— " + hd.collectionName
                        + " " + hd.numberInBook);
                startActivity(Intent.createChooser(send, getString(R.string.action_share)));
            });
        }

        @Override
        public int getItemCount() {
            return hadiths.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView body;
        final TextView gradings;
        final TextView takhrij;
        final View btnBookmark;
        final View btnCopy;
        final View btnShare;

        Holder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.tv_hadith_number);
            body = itemView.findViewById(R.id.tv_hadith_body);
            gradings = itemView.findViewById(R.id.tv_gradings);
            takhrij = itemView.findViewById(R.id.tv_takhrij);
            btnBookmark = itemView.findViewById(R.id.btn_bookmark);
            btnCopy = itemView.findViewById(R.id.btn_copy);
            btnShare = itemView.findViewById(R.id.btn_share);
        }
    }
}
