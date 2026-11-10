package com.ikp.dev.app.ui.utilities;

import android.content.Context;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.ikp.dev.app.R;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihCounterEntity;
import com.ikp.dev.app.data.local.entity.ContentCacheEntities.TasbihDailyEntity;
import com.ikp.dev.app.di.ServiceLocator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Offline Tasbih counter with persistent counters, daily totals, target presets and history. */
public class TasbihFragment extends Fragment {

    private TextView countText;
    private TextView progressText;
    private TextView dailyText;
    private TextView totalText;
    private TextView targetStatText;
    private TextView sessionNameText;
    private TextView streakText;
    private TextView lastSessionText;
    private View countButton;
    private SwitchMaterial vibrationSwitch;
    private final List<TasbihCounterEntity> counters = new ArrayList<>();
    private long selectedId = -1;
    private int currentCount;
    private int currentTarget = 100;
    private String currentLabel = "SubhanAllah";
    private boolean bindingVibrationSwitch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasbih, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        countText = view.findViewById(R.id.tv_count);
        progressText = view.findViewById(R.id.tv_progress);
        dailyText = view.findViewById(R.id.tv_daily);
        totalText = view.findViewById(R.id.tv_total);
        targetStatText = view.findViewById(R.id.tv_target_stat);
        sessionNameText = view.findViewById(R.id.tv_session_name);
        streakText = view.findViewById(R.id.tv_streak);
        lastSessionText = view.findViewById(R.id.tv_last_session);
        countButton = view.findViewById(R.id.btn_count);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        countButton.setOnClickListener(v -> increment());
        view.findViewById(R.id.btn_tap_hint).setOnClickListener(v -> increment());
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> resetCurrent());
        view.findViewById(R.id.btn_save).setOnClickListener(v -> saveSession());
        view.findViewById(R.id.btn_new_counter).setOnClickListener(v -> newCounter());
        view.findViewById(R.id.btn_history).setOnClickListener(v -> showHistory());
        view.findViewById(R.id.btn_session).setOnClickListener(v -> chooseSession());

        vibrationSwitch = view.findViewById(R.id.switch_vibration);
        bindingVibrationSwitch = true;
        vibrationSwitch.setChecked(ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true));
        bindingVibrationSwitch = false;
        vibrationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingVibrationSwitch) return;
            ServiceLocator.appPrefs().edit().putBoolean("tasbih_vibrate", checked).apply();
            persistCounterSettings();
        });

        view.findViewById(R.id.target_33).setOnClickListener(v -> setTarget(33));
        view.findViewById(R.id.target_99).setOnClickListener(v -> setTarget(99));
        view.findViewById(R.id.target_100).setOnClickListener(v -> setTarget(100));
        view.findViewById(R.id.target_1000).setOnClickListener(v -> setTarget(1000));
        loadCounters();
    }

    private void loadCounters() {
        ServiceLocator.executors().single.execute(() -> {
            List<TasbihCounterEntity> loaded = ServiceLocator.database().contentDao().counters();
            if (loaded.isEmpty()) {
                TasbihCounterEntity initial = new TasbihCounterEntity();
                initial.label = "SubhanAllah";
                initial.count = 0;
                initial.target = 100;
                initial.vibrate = ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true);
                initial.sound = false;
                initial.updatedAtEpochMs = System.currentTimeMillis();
                ServiceLocator.database().contentDao().upsertCounter(initial);
                loaded = ServiceLocator.database().contentDao().counters();
            }
            List<TasbihCounterEntity> finalLoaded = loaded;
            List<TasbihDailyEntity> history = ServiceLocator.database().contentDao().dailyHistory(3650);
            String today = dateKey(new Date());
            TasbihDailyEntity todayTotal = ServiceLocator.database().contentDao().dailyTotal(today);
            long lifetime = ServiceLocator.database().contentDao().lifetimeDhikrTotal();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                counters.clear();
                counters.addAll(finalLoaded);
                TasbihCounterEntity selected = findSelected(finalLoaded);
                if (selected == null) selected = finalLoaded.get(0);
                selectedId = selected.counterId;
                currentCount = Math.max(0, selected.count);
                currentTarget = selected.target == null || selected.target <= 0 ? 100 : selected.target;
                currentLabel = selected.label;
                renderCounter();
                dailyText.setText(String.valueOf(todayTotal == null ? 0 : todayTotal.total));
                totalText.setText(String.format(Locale.getDefault(), "%,d", lifetime));
                streakText.setText(streakLength(history) + (streakLength(history) == 1 ? " day" : " days"));
                int last = ServiceLocator.appPrefs().getInt("tasbih_last_session_count", -1);
                lastSessionText.setText(last < 0 ? "No saved session" :
                        String.format(Locale.getDefault(), "%,d dhikr", last));
                bindingVibrationSwitch = true;
                vibrationSwitch.setChecked(ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true));
                bindingVibrationSwitch = false;
                renderTargetButtons();
            });
        });
    }

    @Nullable
    private TasbihCounterEntity findSelected(List<TasbihCounterEntity> rows) {
        for (TasbihCounterEntity row : rows) {
            if (row.counterId == selectedId) return row;
        }
        return null;
    }

    private void renderCounter() {
        if (countText == null) return;
        countText.setText(String.format(Locale.getDefault(), "%,d", currentCount));
        progressText.setText(String.format(Locale.getDefault(), "of %,d", currentTarget));
        targetStatText.setText(String.format(Locale.getDefault(), "%,d", currentTarget));
        sessionNameText.setText(currentLabel);
        renderTargetButtons();
    }

    private void increment() {
        if (selectedId < 0) return;
        currentCount++;
        renderCounter();
        long id = selectedId;
        int count = currentCount;
        long now = System.currentTimeMillis();
        if (ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true)) vibrate();
        if (ServiceLocator.appPrefs().getBoolean("tasbih_sound", false)) beep();
        if (count >= currentTarget && count - 1 < currentTarget) {
            Toast.makeText(requireContext(), "Target reached. Alhamdulillah!", Toast.LENGTH_SHORT).show();
        }
        ServiceLocator.executors().single.execute(() -> {
            String today = dateKey(new Date(now));
            ServiceLocator.database().contentDao().setCount(id, count, now);
            TasbihDailyEntity day = ServiceLocator.database().contentDao().dailyTotal(today);
            TasbihDailyEntity updated = day == null ? new TasbihDailyEntity() : day;
            updated.day = today;
            updated.total = (day == null ? 0 : day.total) + 1;
            updated.updatedAtEpochMs = now;
            ServiceLocator.database().contentDao().upsertDaily(updated);
            long lifetime = ServiceLocator.database().contentDao().lifetimeDhikrTotal();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (dailyText != null) dailyText.setText(String.valueOf(updated.total));
                if (totalText != null) totalText.setText(String.format(Locale.getDefault(), "%,d", lifetime));
            });
        });
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //noinspection deprecation
                vibrator.vibrate(28);
            }
        } catch (RuntimeException ignored) {
            // Device vibration is optional; counting remains available.
        }
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
            tone.release();
        } catch (RuntimeException ignored) {
            // Sound is optional.
        }
    }

    private void resetCurrent() {
        if (selectedId < 0) return;
        currentCount = 0;
        renderCounter();
        long id = selectedId;
        ServiceLocator.executors().single.execute(() ->
                ServiceLocator.database().contentDao().setCount(id, 0, System.currentTimeMillis()));
    }

    private void saveSession() {
        if (selectedId < 0) return;
        long now = System.currentTimeMillis();
        int savedCount = currentCount;
        long id = selectedId;
        TasbihCounterEntity current = findSelected(counters);
        if (current != null) {
            current.count = savedCount;
            current.target = currentTarget;
            current.vibrate = ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true);
            current.updatedAtEpochMs = now;
        }
        ServiceLocator.appPrefs().edit()
                .putInt("tasbih_last_session_count", savedCount)
                .putLong("tasbih_last_session_time", now)
                .putString("tasbih_last_session_label", currentLabel)
                .apply();
        ServiceLocator.executors().single.execute(() ->
                ServiceLocator.database().contentDao().setCount(id, savedCount, now));
        lastSessionText.setText(String.format(Locale.getDefault(), "%,d dhikr", savedCount));
        Toast.makeText(requireContext(), "Session saved", Toast.LENGTH_SHORT).show();
    }

    private void setTarget(int target) {
        currentTarget = target;
        renderCounter();
        persistCounterSettings();
    }

    private void renderTargetButtons() {
        if (getView() == null) return;
        int[] ids = {R.id.target_33, R.id.target_99, R.id.target_100, R.id.target_1000};
        int[] values = {33, 99, 100, 1000};
        int green = requireContext().getColor(R.color.brand_green_dark);
        int ivory = requireContext().getColor(R.color.library_card);
        int white = requireContext().getColor(R.color.white);
        int ink = requireContext().getColor(R.color.text_headline_dark);
        for (int i = 0; i < ids.length; i++) {
            MaterialButton button = getView().findViewById(ids[i]);
            boolean selected = currentTarget == values[i];
            button.setBackgroundTintList(ColorStateList.valueOf(selected ? green : ivory));
            button.setTextColor(selected ? white : ink);
            button.setStrokeColor(ColorStateList.valueOf(selected ?
                    requireContext().getColor(R.color.brand_gold_accent) :
                    requireContext().getColor(R.color.outline_soft)));
            button.setStrokeWidth(selected ? dp(2) : dp(1));
        }
    }

    private void persistCounterSettings() {
        if (selectedId < 0) return;
        long id = selectedId;
        int target = currentTarget;
        boolean vibrate = ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true);
        ServiceLocator.executors().single.execute(() -> {
            TasbihCounterEntity counter = ServiceLocator.database().contentDao().counter(id);
            if (counter == null) return;
            counter.target = target;
            counter.vibrate = vibrate;
            counter.updatedAtEpochMs = System.currentTimeMillis();
            ServiceLocator.database().contentDao().upsertCounter(counter);
        });
    }

    private void chooseSession() {
        if (counters.isEmpty()) return;
        String[] labels = new String[counters.size()];
        for (int i = 0; i < counters.size(); i++) {
            TasbihCounterEntity counter = counters.get(i);
            labels[i] = counter.label + "  ·  " + String.format(Locale.getDefault(), "%,d", counter.count);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose a dhikr")
                .setItems(labels, (dialog, which) -> {
                    TasbihCounterEntity choice = counters.get(which);
                    selectedId = choice.counterId;
                    currentCount = choice.count;
                    currentTarget = choice.target == null || choice.target <= 0 ? 100 : choice.target;
                    currentLabel = choice.label;
                    renderCounter();
                })
                .setNeutralButton(R.string.tasbih_new_counter, (dialog, which) -> newCounter())
                .show();
    }

    private void newCounter() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.tasbih_label_hint);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tasbih_new_counter)
                .setView(input)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter a name for this dhikr", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ServiceLocator.executors().single.execute(() -> {
                        TasbihCounterEntity counter = new TasbihCounterEntity();
                        counter.label = label;
                        counter.count = 0;
                        counter.target = currentTarget;
                        counter.vibrate = ServiceLocator.appPrefs().getBoolean("tasbih_vibrate", true);
                        counter.sound = false;
                        counter.updatedAtEpochMs = System.currentTimeMillis();
                        long newId = ServiceLocator.database().contentDao().upsertCounter(counter);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            selectedId = newId;
                            currentCount = 0;
                            currentLabel = label;
                            renderCounter();
                            loadCounters();
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHistory() {
        ServiceLocator.executors().single.execute(() -> {
            List<TasbihDailyEntity> days = ServiceLocator.database().contentDao().dailyHistory(30);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (days.isEmpty()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Dhikr history")
                            .setMessage("Your daily totals will appear here as you count.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                StringBuilder message = new StringBuilder();
                SimpleDateFormat output = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                for (TasbihDailyEntity day : days) {
                    String label = day.day;
                    try {
                        Date parsed = input.parse(day.day);
                        if (parsed != null) label = output.format(parsed);
                    } catch (java.text.ParseException ignored) {
                        // Keep the stored ISO date if parsing fails.
                    }
                    message.append(label).append("    ")
                            .append(String.format(Locale.getDefault(), "%,d dhikr", day.total)).append('\n');
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Dhikr history · last 30 days")
                        .setMessage(message.toString().trim())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        });
    }

    private int streakLength(List<TasbihDailyEntity> days) {
        Set<String> activeDays = new HashSet<>();
        for (TasbihDailyEntity day : days) activeDays.add(day.day);
        Calendar cursor = Calendar.getInstance();
        if (!activeDays.contains(dateKey(cursor.getTime()))) cursor.add(Calendar.DAY_OF_YEAR, -1);
        int streak = 0;
        while (activeDays.contains(dateKey(cursor.getTime()))) {
            streak++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private String dateKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
