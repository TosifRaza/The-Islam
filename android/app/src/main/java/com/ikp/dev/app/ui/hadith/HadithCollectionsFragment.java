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

/** The six collections (M4 §8) with edition/numbering provenance + QA state. */
public class HadithCollectionsFragment extends Fragment {

    private final List<ContentDtos.HadithCollectionDto> rows = new ArrayList<>();
    private CollectionAdapter adapter;
    private TextView stateText;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hadith_collections, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        stateText = v.findViewById(R.id.state_text);
        recycler = v.findViewById(R.id.recycler);
        adapter = new CollectionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        v.findViewById(R.id.btn_retry).setOnClickListener(x -> load());
        load();
    }

    private void load() {
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.HadithCollectionDto>> r =
                    ServiceLocator.contentRepository().hadithCollections();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null && !r.value.isEmpty()) {
                    rows.clear();
                    rows.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                } else {
                    stateText.setText(R.string.state_error_offline);
                    if (getView() != null) getView().findViewById(R.id.btn_retry).setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private final class CollectionAdapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hadith_collection_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.HadithCollectionDto c = rows.get(position);
            h.nameEn.setText(c.nameEn);
            h.nameAr.setText(c.nameAr);
            h.meta.setText(getString(R.string.hadith_collection_meta, c.hadithCount, c.bookCount));
            h.qa.setText("passed".equals(c.scholarlyQaStatus)
                    ? "" : getString(R.string.hadith_qa_pending));
            h.qa.setVisibility("passed".equals(c.scholarlyQaStatus) ? View.GONE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> {
                android.os.Bundle args = new android.os.Bundle();
                args.putString("collectionCode", c.code);
                args.putString("collectionName", c.nameEn);
                NavHostFragment.findNavController(HadithCollectionsFragment.this)
                        .navigate(R.id.hadithBooksFragment, args);
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView nameEn;
        final TextView nameAr;
        final TextView meta;
        final TextView qa;

        Holder(@NonNull View itemView) {
            super(itemView);
            nameEn = itemView.findViewById(R.id.tv_name_en);
            nameAr = itemView.findViewById(R.id.tv_name_ar);
            meta = itemView.findViewById(R.id.tv_meta);
            qa = itemView.findViewById(R.id.tv_qa);
        }
    }
}
