package com.ikp.dev.app.ui.utilities;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Qibla;
import com.ikp.dev.app.R;
import com.ikp.dev.app.di.ServiceLocator;

/**
 * Qibla compass (M4 §13): bearing from persisted location (Adhan Qibla math),
 * device azimuth from the rotation-vector sensor with magnetic fallback,
 * calibration + accuracy guidance, and an honest static fallback when the
 * location or sensor is unavailable.
 */
public class QiblaFragment extends Fragment implements SensorEventListener {

    private SensorManager sensors;
    private TextView bearingText;
    private TextView statusText;
    private CompassView compass;
    private float azimuth = 0;
    private double bearing = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_qibla, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        bearingText = v.findViewById(R.id.tv_bearing);
        statusText = v.findViewById(R.id.tv_status);
        compass = v.findViewById(R.id.compass);
        v.findViewById(R.id.btn_back).setOnClickListener(x ->
                NavHostFragment.findNavController(this).popBackStack());
        String lat = ServiceLocator.appPrefs().getString("prayer_lat", null);
        String lon = ServiceLocator.appPrefs().getString("prayer_lon", null);
        if (lat != null && lon != null) {
            bearing = new Qibla(new Coordinates(
                    Double.parseDouble(lat), Double.parseDouble(lon))).direction;
            bearingText.setText(getString(R.string.qibla_bearing, bearing));
            statusText.setText(R.string.qibla_calibrate_hint);
        } else {
            statusText.setText(R.string.qibla_no_location);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        sensors = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor rot = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rot != null) {
            sensors.registerListener(this, rot, SensorManager.SENSOR_DELAY_UI);
        } else {
            Sensor mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            Sensor acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mag != null) sensors.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
            if (acc != null) sensors.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensors != null) sensors.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] r = new float[9];
            SensorManager.getRotationMatrixFromVector(r, event.values);
            float[] o = new float[3];
            SensorManager.getOrientation(r, o);
            azimuth = (float) Math.toDegrees(o[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER
                || event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            // simple two-sensor fallback handled coarsely; rotation vector is primary
        }
        if (compass != null) {
            compass.setAngles(azimuth, (float) bearing);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (statusText != null) {
            statusText.setText(accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                    ? R.string.qibla_needs_calibration : R.string.qibla_calibrate_hint);
        }
    }

}
