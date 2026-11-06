package com.ikp.dev.app.ui.utilities;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihCounterEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihDailyEntity;
import com.ikp.dev.app.di.ServiceLocator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tasbih counter (M4 §13 / UTL-04): FULLY OFFLINE with Room (saved counters,
 * daily totals, history), vibration + optional beep, long-press reset.
 */
public class TasbihFragment extends Fragment {

    private TextView countText;
    private TextView dailyText;
    private CounterAdapter adapter;
    private long selectedId = -1;
    private int currentCount = 0;
    private final List<TasbihCounterEntity> counters = new java.util.ArrayList<>();
    private final List<TasbihDailyEntity> history = new java.util.ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasbih, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        countText = v.findViewById(R.id.tv_count);
        dailyText = v.findViewById(R.id.tv_daily);
        RecyclerView recycler = v.findViewById(R.id.recycler);
        adapter = new CounterAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        View big = v.findViewById(R.id.btn_count);
        big.setOnClickListener(x -> increment());
        big.setOnLongClickListener(x -> {
            resetCurrent();
            return true;
        });
        v.findViewById(R.id.btn_new_counter).setOnClickListener(x -> newCounter());
        loadCounters();
    }

    private void loadCounters() {
        ServiceLocator.executors().io.execute(() -> {
            List<TasbihCounterEntity> c = ServiceLocator.database().contentDao().counters();
            List<TasbihDailyEntity> h = ServiceLocator.database().contentDao().dailyHistory(7);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            TasbihDailyEntity daily = ServiceLocator.database().contentDao().dailyTotal(today);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                counters.clear();
                counters.addAll(c);
                history.clear();
                history.addAll(h);
                adapter.notifyDataSetChanged();
                if (selectedId < 0 && !c.isEmpty()) {
                    selectedId = c.get(0).counterId;
                    currentCount = c.get(0).count;
                } else {
                    for (TasbihCounterEntity e : c) {
                        if (e.counterId == selectedId) currentCount = e.count;
                    }
                }
                if (countText != null) {
                    countText.setText(String.valueOf(currentCount));
                }
                View btn = getView() != null ? getView().findViewById(R.id.btn_count) : null;
                if (btn instanceof android.widget.TextView) {
                    ((android.widget.TextView) btn).setText(String.valueOf(currentCount));
                }
                if (dailyText != null) {
                    dailyText.setText(getString(R.string.tasbih_daily_total,
                            daily == null ? 0 : daily.total));
                }
            });
        });
    }

    private void increment() {
        currentCount++;
        if (countText != null) {
            countText.setText(String.valueOf(currentCount));
        }
        View btn = getView() != null ? getView().findViewById(R.id.btn_count) : null;
        if (btn instanceof android.widget.TextView) {
            ((android.widget.TextView) btn).setText(String.valueOf(currentCount));
        }
        if (ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true)) {
            try {
                Vibrator vib = (Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
                if (vib != null && vib.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vib.vibrate(30);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (ServiceLocator.appPrefs().getBoolean("tasbih_sound", false)) {
            try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
            } catch (RuntimeException ignored) {
                // tone output is optional; the count never depends on it
            }
        }
        ServiceLocator.executors().io.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            ServiceLocator.database().contentDao().setCount(selectedId, currentCount, System.currentTimeMillis());
            TasbihDailyEntity day = ServiceLocator.database().contentDao().dailyTotal(today);
            TasbihDailyEntity e = day == null ? new TasbihDailyEntity() : day;
            e.day = today;
            e.total = (day == null ? 0 : day.total) + 1;
            e.updatedAtEpochMs = System.currentTimeMillis();
            ServiceLocator.database().contentDao().upsertDaily(e);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> dailyText.setText(
                    getString(R.string.tasbih_daily_total, e.total)));
        });
    }

    private void resetCurrent() {
        currentCount = 0;
        countText.setText("0");
        ServiceLocator.executors().io.execute(() ->
                ServiceLocator.database().contentDao().setCount(selectedId, 0, System.currentTimeMillis()));
    }

    private void newCounter() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.tasbih_label_hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tasbih_new_counter)
                .setView(input)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) return;
                    ServiceLocator.executors().io.execute(() -> {
                        TasbihCounterEntity e = new TasbihCounterEntity();
                        e.label = label;
                        e.count = 0;
                        e.vibrate = true;
                        e.sound = false;
                        e.updatedAtEpochMs = System.currentTimeMillis();
                        long id = ServiceLocator.database().contentDao().upsertCounter(e);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            selectedId = id;
                            currentCount = 0;
                            loadCounters();
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final class CounterAdapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tasbih_counter, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            TasbihCounterEntity c = counters.get(position);
            h.label.setText(c.label);
            h.count.setText(String.valueOf(c.count));
            h.itemView.setOnClickListener(v -> {
                selectedId = c.counterId;
                currentCount = c.count;
                countText.setText(String.valueOf(currentCount));
            });
            h.itemView.setOnLongClickListener(v -> {
                ServiceLocator.executors().io.execute(() ->
                        ServiceLocator.database().contentDao().deleteCounter(c.counterId));
                loadCounters();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return counters.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView count;

        Holder(@NonNull View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            count = v.findViewById(R.id.tv_count);
        }
    }
}
