package com.ikp.dev.app.ui.utilities;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;
import com.ikp.dev.app.R;
import com.ikp.dev.app.di.ServiceLocator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Prayer Times (M4 §13): computed ON DEVICE with the MIT-licensed Adhan
 * library (SR-17) — fully offline. Location via presets, manual entry, or
 * last-known device location when permitted (permission-denied falls back to
 * presets + manual fields, never blocking the screen).
 */
public class PrayerTimesFragment extends Fragment {

    private static final double[][] CITIES = {
            {21.4225, 39.8262},   // Makkah
            {24.4686, 39.6142},   // Madinah
            {31.7683, 35.2137},   // Jerusalem
            {41.0082, 28.9784},   // Istanbul
            {30.0444, 31.2357},   // Cairo
            {24.8607, 67.0011},   // Karachi
            {28.6139, 77.2090},   // Delhi
            {-6.2088, 106.8456},  // Jakarta
            {51.5074, -0.1278},   // London
            {40.7128, -74.0060},  // New York
    };

    private TextView out;
    private EditText latInput;
    private EditText lonInput;
    private Spinner methodSpinner;
    private TextView permissionNote;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_prayer_times, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        out = v.findViewById(R.id.tv_times);
        latInput = v.findViewById(R.id.input_lat);
        lonInput = v.findViewById(R.id.input_lon);
        methodSpinner = v.findViewById(R.id.spinner_method);
        permissionNote = v.findViewById(R.id.tv_permission_note);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        methodSpinner.setAdapter(ArrayAdapter.createFromResource(requireContext(),
                R.array.prayer_methods, android.R.layout.simple_spinner_dropdown_item));
        String[] cityNames = getResources().getStringArray(R.array.prayer_cities);
        Spinner citySpinner = v.findViewById(R.id.spinner_city);
        citySpinner.setAdapter(ArrayAdapter.createFromResource(requireContext(),
                R.array.prayer_cities, android.R.layout.simple_spinner_dropdown_item));
        v.findViewById(R.id.btn_use_city).setOnClickListener(x ->
                compute(CITIES[citySpinner.getSelectedItemPosition()][0],
                        CITIES[citySpinner.getSelectedItemPosition()][1]));
        v.findViewById(R.id.btn_use_manual).setOnClickListener(x -> {
            try {
                compute(Double.parseDouble(latInput.getText().toString()),
                        Double.parseDouble(lonInput.getText().toString()));
            } catch (NumberFormatException e) {
                out.setText(R.string.prayer_invalid_coords);
            }
        });
        v.findViewById(R.id.btn_use_device).setOnClickListener(x -> useDeviceLocation());
    }

    private void useDeviceLocation() {
        try {
            LocationManager lm = (LocationManager)
                    requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                permissionNote.setVisibility(View.GONE);
                compute(best.getLatitude(), best.getLongitude());
            } else {
                permissionNote.setVisibility(View.VISIBLE);
                permissionNote.setText(R.string.prayer_permission_denied);
            }
        } catch (RuntimeException e) {
            permissionNote.setVisibility(View.VISIBLE);
            permissionNote.setText(R.string.prayer_permission_denied);
        }
    }

    private void compute(double lat, double lon) {
        ServiceLocator.appPrefs().edit()
                .putString("prayer_lat", String.valueOf(lat))
                .putString("prayer_lon", String.valueOf(lon)).apply();
        ServiceLocator.executors().io.execute(() -> {
            CalculationParameters params = CalculationMethod.MUSLIM_WORLD_LEAGUE.getParameters();
            String method = ServiceLocator.appPrefs().getString("prayer_method", "muslim_world_league");
            switch (method == null ? "" : method) {
                case "egyptian" -> params = CalculationMethod.EGYPTIAN.getParameters();
                case "umm_al_qura" -> params = CalculationMethod.UMM_AL_QURA.getParameters();
                case "karachi" -> params = CalculationMethod.KARACHI.getParameters();
                case "dubai" -> params = CalculationMethod.DUBAI.getParameters();
                case "north_america" -> params = CalculationMethod.NORTH_AMERICA.getParameters();
                case "kuwait" -> params = CalculationMethod.KUWAIT.getParameters();
                case "qatar" -> params = CalculationMethod.QATAR.getParameters();
                case "moonsighting" -> params = CalculationMethod.MOON_SIGHTING_COMMITTEE.getParameters();
                default -> { }
            }
            String madhab = ServiceLocator.appPrefs().getString("prayer_madhab", "shafi");
            params.madhab = "hanafi".equals(madhab) ? Madhab.HANAFI : Madhab.SHAFI;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            PrayerTimes times = new PrayerTimes(new Coordinates(lat, lon),
                    new DateComponents(cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)), params);
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", java.util.Locale.US);
            fmt.setTimeZone(TimeZone.getDefault());
            String text = getString(R.string.prayer_fajr) + " " + fmt.format(times.fajr)
                    + "\n" + getString(R.string.prayer_sunrise) + " " + fmt.format(times.sunrise)
                    + "\n" + getString(R.string.prayer_dhuhr) + " " + fmt.format(times.dhuhr)
                    + "\n" + getString(R.string.prayer_asr) + " " + fmt.format(times.asr)
                    + "\n" + getString(R.string.prayer_maghrib) + " " + fmt.format(times.maghrib)
                    + "\n" + getString(R.string.prayer_isha) + " " + fmt.format(times.isha);
            String result = text;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> out.setText(result));
        });
    }
}
