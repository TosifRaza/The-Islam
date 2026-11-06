package com.ikp.dev.app.ui.hadith;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

/** Books of one hadith collection (M4 §8, HAD-02 navigation level). */
public class HadithBooksFragment extends Fragment {

    private final List<ContentDtos.HadithBookDto> rows = new ArrayList<>();
    private BookAdapter adapter;
    private TextView stateText;
    private TextView title;
    private String collectionCode = "bukhari";
    private String collectionName = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hadith_books, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            collectionCode = args.getString("collectionCode", "bukhari");
            collectionName = args.getString("collectionName", "");
        }
        title = v.findViewById(R.id.title);
        title.setText(collectionName);
        stateText = v.findViewById(R.id.state_text);
        View retryBtn = v.findViewById(R.id.btn_retry);
        if (retryBtn != null) retryBtn.setVisibility(View.GONE);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new BookAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        if (retryBtn != null) {
            retryBtn.setOnClickListener(x -> load());
        }
        load();
    }

    private void load() {
        if (getView() != null) {
            View retry = getView().findViewById(R.id.btn_retry);
            if (retry != null) retry.setVisibility(View.GONE);
        }
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.HadithBookDto>> r =
                    ServiceLocator.contentRepository().hadithBooks(collectionCode);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null && !r.value.isEmpty()) {
                    rows.clear();
                    rows.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(View.GONE);
                    if (getView() != null) {
                        View retry = getView().findViewById(R.id.btn_retry);
                        if (retry != null) retry.setVisibility(View.GONE);
                    }
                } else {
                    stateText.setText(R.string.state_error_offline);
                    if (getView() != null) {
                        View retry = getView().findViewById(R.id.btn_retry);
                        if (retry != null) retry.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private final class BookAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hadith_book_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.HadithBookDto b = rows.get(position);
            h.name.setText(b.nameEn == null ? getString(R.string.hadith_book_number, b.bookNumber)
                    : getString(R.string.hadith_book_number, b.bookNumber) + " — " + b.nameEn);
            h.meta.setText(getString(R.string.hadith_book_meta, b.hadithCount));
            h.itemView.setOnClickListener(v -> {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("bookId", b.id);
                args.putString("bookName", b.nameEn);
                args.putString("collectionCode", collectionCode);
                NavHostFragment.findNavController(HadithBooksFragment.this)
                        .navigate(R.id.hadithReaderFragment, args);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            meta = itemView.findViewById(R.id.tv_meta);
        }
    }
}
