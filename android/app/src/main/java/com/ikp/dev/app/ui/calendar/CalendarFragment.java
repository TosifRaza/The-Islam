package com.ikp.dev.app.ui.calendar;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ikp.dev.app.R;
import com.ikp.dev.app.core.common.Outcome;
import com.ikp.dev.app.data.remote.dto.ContentDtos;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Islamic Calendar (M4 §12). Today card states clearly whether the Hijri
 * date is CALCULATED or adjusted by a locally-confirmed offset; events show
 * their basis; reminder setting stores an honest "arrives with notifications
 * milestone" note.
 */
public class CalendarFragment extends Fragment {

    private final List<ContentDtos.IslamicEventDto> events = new ArrayList<>();
    private EventAdapter adapter;
    private TextView todayCard;
    private TextView stateText;
    private int offsetDays = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        todayCard = v.findViewById(R.id.tv_today);
        stateText = v.findViewById(R.id.state_text);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new EventAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        v.findViewById(R.id.btn_offset).setOnClickListener(x -> chooseOffset());
        offsetDays = ServiceLocator.appPrefs().getInt("calendar_offset_days", 0);
        loadToday();
        loadEvents();
    }

    private void chooseOffset() {
        String[] options = new String[]{"-3", "-2", "-1", "0", "+1", "+2", "+3"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.calendar_offset_title)
                .setItems(options, (d, w) -> {
                    offsetDays = Integer.parseInt(options[w].replace("+", ""));
                    ServiceLocator.appPrefs().edit().putInt("calendar_offset_days", offsetDays).apply();
                    loadToday();
                }).show();
    }

    private void loadToday() {
        todayCard.setText(R.string.state_loading);
        ServiceLocator.executors().io.execute(() -> {
            Outcome<ContentDtos.CalendarTodayDto> r =
                    ServiceLocator.contentRepository().calendarToday(offsetDays, "UTC");
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null && r.value.hijri != null) {
                    todayCard.setText(getString(R.string.calendar_today,
                            r.value.hijri.day, r.value.hijri.month, r.value.hijri.year,
                            r.value.gregorian,
                            r.value.calculated ? getString(R.string.calendar_calculated)
                                    : getString(R.string.calendar_confirmed)));
                } else {
                    todayCard.setText(R.string.state_error_offline);
                }
            });
        });
    }

    private void loadEvents() {
        stateText.setText(R.string.state_loading);
        stateText.setVisibility(View.VISIBLE);
        ServiceLocator.executors().io.execute(() -> {
            Outcome<List<ContentDtos.IslamicEventDto>> r =
                    ServiceLocator.contentRepository().calendarEvents(null);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.isSuccess() && r.value != null) {
                    events.clear();
                    events.addAll(r.value);
                    adapter.notifyDataSetChanged();
                    stateText.setVisibility(View.GONE);
                } else {
                    stateText.setText(R.string.state_error_offline);
                }
            });
        });
    }

    private final class EventAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ContentDtos.IslamicEventDto e = events.get(position);
            h.name.setText(e.nameEn);
            String date = e.gregorianDate == null ? "" : " · " + e.gregorianDate;
            h.meta.setText(getString(R.string.calendar_event_meta, e.hijriMonth, e.hijriDay) + date
                    + ("calculated".equals(e.basis) ? " · " + getString(R.string.calendar_calculated) : ""));
        }

        @Override
        public int getItemCount() {
            return events.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;

        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_name);
            meta = v.findViewById(R.id.tv_meta);
        }
    }
}
