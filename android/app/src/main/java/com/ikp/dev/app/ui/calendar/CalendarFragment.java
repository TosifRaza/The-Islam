package com.ikp.dev.app.ui.calendar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Calculated Hijri calendar and upcoming event dates, with a user-controlled local offset. */
public class CalendarFragment extends Fragment {

    private static final String[] HIJRI_MONTHS = {
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Awwal",
            "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qi'dah", "Dhu al-Hijjah"
    };

    private final List<EventOccurrence> events = new ArrayList<>();
    private EventAdapter adapter;
    private View stateText;
    private View retryButton;
    private TextView hijriDayText;
    private TextView hijriMonthText;
    private TextView hijriYearText;
    private TextView weekdayText;
    private TextView gregorianText;
    private TextView calculatedText;
    private TextView offsetText;
    private TextView viewAllText;
    private int offsetDays;
    private boolean showAllEvents;
    private int loadGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        hijriDayText = view.findViewById(R.id.tv_hijri_day);
        hijriMonthText = view.findViewById(R.id.tv_hijri_month);
        hijriYearText = view.findViewById(R.id.tv_hijri_year);
        weekdayText = view.findViewById(R.id.tv_weekday);
        gregorianText = view.findViewById(R.id.tv_gregorian);
        calculatedText = view.findViewById(R.id.tv_calculated);
        offsetText = view.findViewById(R.id.tv_offset_value);
        viewAllText = view.findViewById(R.id.btn_view_all);
        stateText = view.findViewById(R.id.state_text);
        retryButton = view.findViewById(R.id.btn_retry);

        RecyclerView recycler = view.findViewById(R.id.recycler);
        adapter = new EventAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_offset).setOnClickListener(v -> chooseOffset());
        retryButton.setOnClickListener(v -> loadCalendar());
        viewAllText.setOnClickListener(v -> {
            showAllEvents = !showAllEvents;
            renderEvents();
        });

        offsetDays = Math.max(-3, Math.min(3,
                ServiceLocator.appPrefs().getInt("calendar_offset_days", 0)));
        renderOffset();
        loadCalendar();
    }

    private void chooseOffset() {
        String[] options = {"-3 days", "-2 days", "-1 day", "0 days · calculated", "+1 day", "+2 days", "+3 days"};
        int selected = offsetDays + 3;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Local Hijri date adjustment")
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    offsetDays = which - 3;
                    ServiceLocator.appPrefs().edit().putInt("calendar_offset_days", offsetDays).apply();
                    renderOffset();
                    dialog.dismiss();
                    loadCalendar();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderOffset() {
        String value = offsetDays == 0 ? "Calculated date · 0 days" :
                String.format(Locale.getDefault(), "Calculated date · %+d %s", offsetDays,
                        Math.abs(offsetDays) == 1 ? "day" : "days");
        offsetText.setText(value);
    }

    private void loadCalendar() {
        stateText.setVisibility(View.VISIBLE);
        stateText.setEnabled(false);
        ((TextView) stateText).setText(R.string.state_loading);
        retryButton.setVisibility(View.GONE);
        events.clear();
        renderEvents();
        final int requestGeneration = ++loadGeneration;
        final int requestedOffset = offsetDays;
        final String zoneId = TimeZone.getDefault().getID();
        ServiceLocator.executors().io.execute(() -> {
            Outcome<ContentDtos.CalendarTodayDto> todayResult =
                    ServiceLocator.contentRepository().calendarToday(requestedOffset, zoneId);
            if (!isAdded()) return;
            if (!todayResult.isSuccess() || todayResult.value == null || todayResult.value.hijri == null
                    || todayResult.value.gregorian == null) {
                requireActivity().runOnUiThread(() -> {
                    if (requestGeneration != loadGeneration) return;
                    ((TextView) stateText).setText(R.string.state_error_offline);
                    stateText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    hijriDayText.setText("—");
                    hijriMonthText.setText("Hijri date unavailable");
                    hijriYearText.setText("— AH");
                    weekdayText.setText("—");
                    gregorianText.setText("—");
                    calculatedText.setText("Today's calculated Hijri date is unavailable offline.");
                });
                return;
            }

            ContentDtos.CalendarTodayDto today = todayResult.value;
            Outcome<List<ContentDtos.IslamicEventDto>> thisYear =
                    ServiceLocator.contentRepository().calendarEvents(today.hijri.year, requestedOffset);
            Outcome<List<ContentDtos.IslamicEventDto>> nextYear =
                    ServiceLocator.contentRepository().calendarEvents(today.hijri.year + 1, requestedOffset);
            List<EventOccurrence> upcoming = new ArrayList<>();
            boolean loadedAnyEvents = false;
            boolean loadedBothYears = thisYear.isSuccess() && thisYear.value != null
                    && nextYear.isSuccess() && nextYear.value != null;
            if (thisYear.isSuccess() && thisYear.value != null) {
                for (ContentDtos.IslamicEventDto event : thisYear.value) {
                    upcoming.add(new EventOccurrence(event, today.hijri.year));
                }
                loadedAnyEvents = true;
            }
            if (nextYear.isSuccess() && nextYear.value != null) {
                for (ContentDtos.IslamicEventDto event : nextYear.value) {
                    upcoming.add(new EventOccurrence(event, today.hijri.year + 1));
                }
                loadedAnyEvents = true;
            }
            upcoming.removeIf(row -> row.event.gregorianDate == null ||
                    row.event.gregorianDate.compareTo(today.gregorian) < 0);
            upcoming.sort((left, right) -> left.event.gregorianDate.compareTo(right.event.gregorianDate));
            boolean eventsLoaded = loadedAnyEvents;
            boolean fullRangeLoaded = loadedBothYears;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (requestGeneration != loadGeneration) return;
                renderToday(today);
                if (eventsLoaded) {
                    events.clear();
                    events.addAll(upcoming);
                    renderEvents();
                    stateText.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                    if (events.isEmpty()) ((TextView) stateText).setText("No upcoming dates are available.");
                    else if (!fullRangeLoaded) {
                        ((TextView) stateText).setText("Some upcoming dates could not be loaded.");
                        stateText.setVisibility(View.VISIBLE);
                    }
                    retryButton.setVisibility(fullRangeLoaded ? View.GONE : View.VISIBLE);
                } else {
                    ((TextView) stateText).setText("Upcoming dates could not be loaded.");
                    stateText.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void renderToday(ContentDtos.CalendarTodayDto today) {
        hijriDayText.setText(String.valueOf(today.hijri.day));
        hijriMonthText.setText(monthName(today.hijri.month));
        hijriYearText.setText(String.format(Locale.getDefault(), "%d AH", today.hijri.year));
        if (today.gregorian != null) {
            String[] dateParts = formatGregorianDate(today.gregorian);
            weekdayText.setText(dateParts[0]);
            gregorianText.setText(dateParts[1]);
        } else {
            weekdayText.setText("—");
            gregorianText.setText("—");
        }
        calculatedText.setText(today.calculated
                ? "Calculated date · not confirmed by local moon sighting"
                : "Locally adjusted date · confirm with your community");
    }

    private String[] formatGregorianDate(String isoDate) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(isoDate);
            if (date == null) return new String[]{"—", isoDate};
            SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.getDefault());
            SimpleDateFormat display = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            weekday.setTimeZone(TimeZone.getTimeZone("UTC"));
            display.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new String[]{weekday.format(date), display.format(date)};
        } catch (ParseException ignored) {
            return new String[]{"—", isoDate};
        }
    }

    private String monthName(int month) {
        return month >= 1 && month <= HIJRI_MONTHS.length ? HIJRI_MONTHS[month - 1] : "Hijri month " + month;
    }

    private String eventDateStatus(ContentDtos.IslamicEventDto event) {
        if (offsetDays != 0) {
            return String.format(Locale.getDefault(), "Adjusted %+d %s · calculated", offsetDays,
                    Math.abs(offsetDays) == 1 ? "day" : "days");
        }
        if ("observed_announced".equals(event.basis)) return "Announced date · verify locally";
        return "Calculated · not sighting-confirmed";
    }

    private String eventShortStatus(ContentDtos.IslamicEventDto event) {
        if (offsetDays != 0) return "Adjusted";
        return "observed_announced".equals(event.basis) ? "Announced" : "Calculated";
    }

    private void renderEvents() {
        if (adapter == null) return;
        int displayed = showAllEvents ? events.size() : Math.min(6, events.size());
        adapter.setDisplayedCount(displayed);
        viewAllText.setText(showAllEvents ? "Show less  ›" : "View all  ›");
        viewAllText.setVisibility(events.size() > 6 ? View.VISIBLE : View.INVISIBLE);
    }

    private String eventTitle(String name) {
        if (name == null || name.trim().isEmpty()) return "Islamic occasion";
        int suffix = name.indexOf(" (");
        return suffix < 0 ? name : name.substring(0, suffix).trim();
    }

    private int iconFor(ContentDtos.IslamicEventDto event) {
        String code = event.code == null ? "" : event.code;
        switch (code) {
            case "islamic_new_year": return R.drawable.ic_3d_calendar_cutout;
            case "ashura": return R.drawable.ic_3d_prayer_cutout;
            case "mawlid_al_nabi": return R.drawable.ic_3d_quran_cutout;
            case "isra_miraj": return R.drawable.ic_3d_qibla_cutout;
            case "laylat_al_qadr": return R.drawable.ic_daily_quran_lantern;
            case "eid_al_fitr": return R.drawable.ic_3d_duas_cutout;
            case "arafah": return R.drawable.ic_3d_qibla_cutout;
            case "eid_al_adha": return R.drawable.ic_3d_hadith_cutout;
            default: return R.drawable.ic_3d_calendar_cutout;
        }
    }

    private void showEventDetails(EventOccurrence occurrence) {
        ContentDtos.IslamicEventDto event = occurrence.event;
        String title = eventTitle(event.nameEn);
        StringBuilder details = new StringBuilder();
        if (event.description != null && !event.description.trim().isEmpty()) {
            details.append(event.description.trim()).append("\n\n");
        }
        details.append(String.format(Locale.getDefault(), "%d %s %d AH", event.hijriDay,
                monthName(event.hijriMonth), occurrence.hijriYear));
        if (event.gregorianDate != null) {
            details.append("\n").append(formatGregorianDate(event.gregorianDate)[0]).append(", ")
                    .append(formatGregorianDate(event.gregorianDate)[1]);
        }
        details.append("\n\n").append(eventDateStatus(event));
        if (event.nameAr != null && !event.nameAr.trim().isEmpty()) details.append("\n\n").append(event.nameAr);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(details.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private final class EventAdapter extends RecyclerView.Adapter<EventHolder> {
        private int displayedCount;

        void setDisplayedCount(int count) {
            displayedCount = count;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public EventHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new EventHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_event, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull EventHolder holder, int position) {
            EventOccurrence occurrence = events.get(position);
            ContentDtos.IslamicEventDto event = occurrence.event;
            holder.icon.setImageResource(iconFor(event));
            holder.name.setText(eventTitle(event.nameEn));
            holder.hijriDate.setText(String.format(Locale.getDefault(), "%d %s", event.hijriDay,
                    monthName(event.hijriMonth)));
            holder.gregorianDate.setText((event.gregorianDate == null ? "Date unavailable" :
                    formatGregorianDate(event.gregorianDate)[1]) + " · " + eventShortStatus(event));
            holder.eventDay.setText(String.valueOf(event.hijriDay));
            holder.eventMonth.setText(monthName(event.hijriMonth));
            holder.eventYear.setText(String.format(Locale.getDefault(), "%d AH", occurrence.hijriYear));
            holder.itemView.setOnClickListener(v -> showEventDetails(occurrence));
        }

        @Override
        public int getItemCount() {
            return Math.min(displayedCount, events.size());
        }
    }

    private static final class EventOccurrence {
        final ContentDtos.IslamicEventDto event;
        final int hijriYear;

        EventOccurrence(ContentDtos.IslamicEventDto event, int hijriYear) {
            this.event = event;
            this.hijriYear = hijriYear;
        }
    }

    private static final class EventHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView hijriDate;
        final TextView gregorianDate;
        final TextView eventDay;
        final TextView eventMonth;
        final TextView eventYear;

        EventHolder(@NonNull View view) {
            super(view);
            icon = view.findViewById(R.id.iv_event_icon);
            name = view.findViewById(R.id.tv_name);
            hijriDate = view.findViewById(R.id.tv_hijri_date);
            gregorianDate = view.findViewById(R.id.tv_gregorian_date);
            eventDay = view.findViewById(R.id.tv_event_day);
            eventMonth = view.findViewById(R.id.tv_event_month);
            eventYear = view.findViewById(R.id.tv_event_year);
        }
    }
}
