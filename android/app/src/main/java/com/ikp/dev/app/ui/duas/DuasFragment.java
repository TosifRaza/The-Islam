package com.ikp.dev.app.ui.duas;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Searchable, category-filtered dua library with offline favourites and device speech. */
public class DuasFragment extends Fragment {
    private final Map<String,String> categories = new LinkedHashMap<>();
    private final List<ContentDtos.DuaDto> rows = new ArrayList<>();
    private final List<ContentDtos.DuaDto> visibleRows = new ArrayList<>();
    private final Set<String> favourites = new HashSet<>();
    private DuaAdapter adapter;
    private TextView stateText;
    private EditText searchField;
    private View retryButton;
    private String selectedCategory;
    private String searchQuery = "";
    private boolean favouritesOnly;
    private TextToSpeech speech;
    private boolean speechReady;
    private long speakingDuaId = -1;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_duas,container,false);
    }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        super.onViewCreated(view,state);
        stateText=view.findViewById(R.id.state_text);
        searchField=view.findViewById(R.id.et_search); retryButton=view.findViewById(R.id.btn_retry);
        RecyclerView recycler=view.findViewById(R.id.recycler);
        adapter=new DuaAdapter(); recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        favourites.addAll(ServiceLocator.appPrefs().getStringSet("duas_favourites",new HashSet<>()));
        speech=new TextToSpeech(requireContext().getApplicationContext(),status -> {
            speechReady=status==TextToSpeech.SUCCESS;
        });

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        retryButton.setOnClickListener(v -> { loadCategories(); loadDuas(); });
        view.findViewById(R.id.btn_filter).setOnClickListener(v -> showFilterOptions());
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                searchQuery=s==null?"":s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchField.setOnEditorActionListener((text,action,event) -> {
            if(action==EditorInfo.IME_ACTION_SEARCH) {
                ((InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(text.getWindowToken(),0);
                return true;
            }
            return false;
        });
        loadCategories(); loadDuas();
    }

    private void loadCategories() {
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.DuaCategoryDto>> result=ServiceLocator.contentRepository().duaCategories();
            if(!isAdded())return;
            requireActivity().runOnUiThread(() -> {
                if(!result.isSuccess()||result.value==null)return;
                categories.clear(); categories.put("",getString(R.string.duas_all));
                for(ContentDtos.DuaCategoryDto c:result.value) {
                    if(c.code!=null&&!c.code.isEmpty()) categories.put(c.code,
                            c.nameEn==null?c.code:c.nameEn);
                }
            });
        });
    }

    private void loadDuas() {
        if(stateText!=null) { stateText.setText(R.string.state_loading); stateText.setVisibility(View.VISIBLE); }
        retryButton.setVisibility(View.GONE);
        final String category=selectedCategory;
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.DuaDto>> result=ServiceLocator.contentRepository().duas(category,1,50);
            if(!isAdded())return;
            requireActivity().runOnUiThread(() -> {
                if(result.isSuccess()&&result.value!=null) {
                    rows.clear(); rows.addAll(result.value); applyFilters();
                } else {
                    stateText.setText(R.string.state_error_offline); stateText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void applyFilters() {
        visibleRows.clear();
        for(ContentDtos.DuaDto dua:rows) {
            if(favouritesOnly&&!favourites.contains(favouriteKey(dua)))continue;
            if(!matchesQuery(dua,searchQuery))continue;
            visibleRows.add(dua);
        }
        adapter.notifyDataSetChanged();
        boolean empty=visibleRows.isEmpty();
        stateText.setVisibility(empty?View.VISIBLE:View.GONE);
        if(empty) stateText.setText(favouritesOnly&&searchQuery.isEmpty()
                ?R.string.duas_no_favourites:R.string.duas_no_matches);
    }

    private boolean matchesQuery(ContentDtos.DuaDto dua,String query) {
        if(query==null||query.isEmpty())return true;
        return contains(dua.title,query)||contains(dua.textAr,query)||contains(dua.transliteration,query)
                ||contains(dua.meaningEn,query)||contains(dua.reference,query)
                ||contains(dua.categoryName,query)||contains(categories.get(dua.categoryCode),query);
    }
    private boolean contains(String value,String query) {
        return value!=null&&value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void showFilterOptions() {
        List<String> categoryCodes=new ArrayList<>(categories.keySet());
        if(!categoryCodes.contains(""))categoryCodes.add(0,"");
        List<String> categoryNames=new ArrayList<>();
        for(String code:categoryCodes) {
            String name=categories.get(code);
            categoryNames.add(name==null?getString(R.string.duas_all):name);
        }

        LinearLayout content=new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(4),dp(24),0);
        TextView categoryLabel=new TextView(requireContext());
        categoryLabel.setText(R.string.duas_filter_category);
        categoryLabel.setTextSize(14);
        categoryLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        categoryLabel.setTextColor(requireContext().getColor(R.color.text_primary));
        content.addView(categoryLabel,new LinearLayout.LayoutParams(-1,-2));

        Spinner categorySpinner=new Spinner(requireContext());
        ArrayAdapter<String> categoryAdapter=new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,categoryNames);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setContentDescription(getString(R.string.duas_filter_category));
        int selectedIndex=categoryCodes.indexOf(selectedCategory==null?"":selectedCategory);
        categorySpinner.setSelection(Math.max(0,selectedIndex));
        content.addView(categorySpinner,new LinearLayout.LayoutParams(-1,dp(52)));

        MaterialCheckBox favouriteCheck=new MaterialCheckBox(requireContext());
        favouriteCheck.setText(R.string.duas_filter_favourites);
        favouriteCheck.setChecked(favouritesOnly);
        content.addView(favouriteCheck,new LinearLayout.LayoutParams(-1,-2));

        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.duas_filter_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.duas_filter_apply,(dialog,which) -> {
                    String code=categoryCodes.get(categorySpinner.getSelectedItemPosition());
                    String nextCategory=code.isEmpty()?null:code;
                    boolean categoryChanged=selectedCategory==null
                            ?nextCategory!=null:!selectedCategory.equals(nextCategory);
                    selectedCategory=nextCategory;
                    favouritesOnly=favouriteCheck.isChecked();
                    if(categoryChanged)loadDuas(); else applyFilters();
                }).show();
    }

    private void openDetails(ContentDtos.DuaDto dua) {
        StringBuilder body=new StringBuilder();
        appendSection(body,dua.textAr);
        appendSection(body,getString(R.string.duas_transliteration)+"\n"+
                (dua.transliteration==null?"":dua.transliteration));
        appendSection(body,dua.meaningEn);
        if(dua.reference!=null&&!dua.reference.isEmpty())
            appendSection(body,getString(R.string.duas_reference)+"\n"+dua.reference);
        if(dua.authenticityNote!=null&&!dua.authenticityNote.isEmpty())appendSection(body,dua.authenticityNote);
        if(dua.sourceAttribution!=null&&!dua.sourceAttribution.isEmpty())appendSection(body,dua.sourceAttribution);
        TextView content=new TextView(requireContext()); content.setText(body.toString());
        content.setTextSize(16); content.setTextColor(requireContext().getColor(R.color.text_primary));
        content.setTextIsSelectable(true); content.setPadding(dp(20),dp(8),dp(20),dp(12));
        content.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(dua.title).setView(content)
                .setPositiveButton(android.R.string.ok,null).show();
    }
    private void appendSection(StringBuilder body,String text) {
        if(text==null||text.trim().isEmpty())return;
        if(body.length()>0)body.append("\n\n"); body.append(text);
    }

    private void shareDua(ContentDtos.DuaDto dua) {
        StringBuilder text=new StringBuilder(dua.title==null?"":dua.title).append("\n\n");
        appendSection(text,dua.textAr); appendSection(text,dua.transliteration);
        appendSection(text,dua.meaningEn); appendSection(text,dua.reference);
        Intent intent=new Intent(Intent.ACTION_SEND); intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT,dua.title); intent.putExtra(Intent.EXTRA_TEXT,text.toString());
        startActivity(Intent.createChooser(intent,getString(R.string.duas_share_title)));
    }

    private void toggleFavourite(ContentDtos.DuaDto dua) {
        String key=favouriteKey(dua);
        if(!favourites.add(key))favourites.remove(key);
        ServiceLocator.appPrefs().edit().putStringSet("duas_favourites",new HashSet<>(favourites)).apply();
        applyFilters();
    }
    private String favouriteKey(ContentDtos.DuaDto dua) { return String.valueOf(dua.id); }

    private void speakDua(ContentDtos.DuaDto dua) {
        if(!speechReady||speech==null) {
            Toast.makeText(requireContext(),R.string.duas_voice_unavailable,Toast.LENGTH_SHORT).show(); return;
        }
        if(speech.isSpeaking()) {
            speech.stop(); speakingDuaId=-1; adapter.notifyDataSetChanged(); return;
        }
        int arabicStatus=speech.setLanguage(new Locale("ar"));
        boolean arabicVoice=arabicStatus>=TextToSpeech.LANG_AVAILABLE;
        String spoken=arabicVoice?dua.textAr:dua.transliteration;
        if(spoken==null||spoken.trim().isEmpty())spoken=dua.meaningEn;
        if(spoken==null||spoken.trim().isEmpty())return;
        if(!arabicVoice)Toast.makeText(requireContext(),R.string.duas_voice_fallback,Toast.LENGTH_SHORT).show();
        speakingDuaId=dua.id;
        Bundle options=new Bundle(); options.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"dua_"+dua.id);
        speech.speak(spoken,TextToSpeech.QUEUE_FLUSH,options,"dua_"+dua.id);
        adapter.notifyDataSetChanged();
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    @Override public void onPause() {
        super.onPause();
        if(speech!=null&&speech.isSpeaking())speech.stop();
        speakingDuaId=-1;
    }
    @Override public void onDestroyView() {
        if(speech!=null) { speech.stop(); speech.shutdown(); speech=null; }
        super.onDestroyView();
    }

    private final class DuaAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dua_card,parent,false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h,int position) {
            ContentDtos.DuaDto dua=visibleRows.get(position);
            String displayTitle=dua.title==null?"":dua.title;
            h.arabic.setText(dua.textAr==null?"":dua.textAr);
            h.meaning.setText(dua.meaningEn==null?"":dua.meaningEn);
            String category=dua.categoryName!=null?dua.categoryName:categories.get(dua.categoryCode);
            int subtitleStart=displayTitle.indexOf(" (");
            if(subtitleStart>0&&displayTitle.endsWith(")")) {
                category=displayTitle.substring(subtitleStart+2,displayTitle.length()-1);
                displayTitle=displayTitle.substring(0,subtitleStart);
            }
            h.title.setText(displayTitle);
            h.category.setText(category==null?"":category);
            h.fixture.setVisibility(Boolean.TRUE.equals(dua.isFixture)?View.VISIBLE:View.GONE);
            boolean isFavourite=favourites.contains(favouriteKey(dua));
            h.favourite.setImageResource(isFavourite?R.drawable.ic_heart_filled:R.drawable.ic_heart_outline);
            h.favourite.setContentDescription(getString(isFavourite?R.string.duas_remove_favourite:R.string.duas_add_favourite));
            h.listen.setText(speakingDuaId==dua.id?R.string.duas_stop:R.string.duas_listen);
            h.listen.setOnClickListener(v -> speakDua(dua));
            h.readMore.setOnClickListener(v -> openDetails(dua));
            h.share.setOnClickListener(v -> shareDua(dua));
            h.favourite.setOnClickListener(v -> toggleFavourite(dua));
            h.itemView.setOnClickListener(v -> openDetails(dua));
        }
        @Override public int getItemCount() { return visibleRows.size(); }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView title,category,arabic,meaning,fixture,readMore;
        final com.google.android.material.button.MaterialButton listen;
        final android.widget.ImageButton share,favourite;
        Holder(@NonNull View view) {
            super(view); title=view.findViewById(R.id.tv_title); category=view.findViewById(R.id.tv_category);
            arabic=view.findViewById(R.id.tv_arabic); meaning=view.findViewById(R.id.tv_meaning);
            fixture=view.findViewById(R.id.tv_fixture);
            listen=view.findViewById(R.id.btn_listen); readMore=view.findViewById(R.id.btn_read_more);
            share=view.findViewById(R.id.btn_share); favourite=view.findViewById(R.id.btn_favourite);
        }
    }
}
