package com.ikp.dev.app.ui.utilities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.Qibla;
import com.ikp.dev.app.R;
import com.ikp.dev.app.di.ServiceLocator;

import java.util.Locale;

/** Qibla direction from saved/device coordinates and live device orientation sensors. */
public class QiblaFragment extends Fragment implements SensorEventListener {
    private static final int LOCATION_REQUEST = 701;
    private SensorManager sensors;
    private Sensor rotationSensor, accelerometer, magnetometer;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable locationTimeout;
    private TextView bearingText, statusText, locationText, coordinatesText, headingText, directionText;
    private CompassView compass;
    private float azimuth;
    private float rawAzimuth;
    private float declination;
    private float[] gravityValues, magneticValues;
    private double bearing;
    private double latitude = Double.NaN, longitude = Double.NaN;
    private boolean hasLocation, hasHeading, lowAccuracy;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_qibla, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        bearingText=view.findViewById(R.id.tv_bearing); statusText=view.findViewById(R.id.tv_status);
        locationText=view.findViewById(R.id.tv_location); coordinatesText=view.findViewById(R.id.tv_coordinates);
        headingText=view.findViewById(R.id.tv_heading); directionText=view.findViewById(R.id.tv_direction);
        compass=view.findViewById(R.id.compass);
        view.findViewById(R.id.btn_back).setOnClickListener(x -> NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btn_calibrate).setOnClickListener(x -> showCalibrationHelp());
        view.findViewById(R.id.btn_location).setOnClickListener(x -> useDeviceLocation());
        readSavedLocation();
        renderLocation();
        renderHeading();
    }

    private void readSavedLocation() {
        String lat=ServiceLocator.appPrefs().getString("prayer_lat", null);
        String lon=ServiceLocator.appPrefs().getString("prayer_lon", null);
        if (lat==null || lon==null) { hasLocation=false; return; }
        try {
            setLocation(Double.parseDouble(lat), Double.parseDouble(lon), false);
        } catch (NumberFormatException ignored) { hasLocation=false; }
    }

    private void setLocation(double lat, double lon, boolean persist) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) return;
        latitude=lat; longitude=lon; hasLocation=true;
        declination=new GeomagneticField((float)lat,(float)lon,0,System.currentTimeMillis()).getDeclination();
        bearing=new Qibla(new Coordinates(lat,lon)).direction;
        if (persist) ServiceLocator.appPrefs().edit().putString("prayer_lat",String.valueOf(lat))
                .putString("prayer_lon",String.valueOf(lon)).apply();
        if (compass!=null) compass.setBearing((float)bearing);
        if (hasHeading) updateHeading(rawAzimuth);
        renderLocation(); renderBearingStatus();
    }

    private void renderLocation() {
        if (locationText==null) return;
        if (hasLocation) {
            coordinatesText.setText(String.format(Locale.getDefault(), "%.4f° %s\n%.4f° %s",
                    Math.abs(latitude),latitude>=0?"N":"S",Math.abs(longitude),longitude>=0?"E":"W"));
            locationText.setText("Using saved location · tap below to refresh");
            bearingText.setText(String.format(Locale.getDefault(), "%.0f°", bearing));
            directionText.setText("Toward Makkah, Saudi Arabia");
        } else {
            coordinatesText.setText("Not set"); locationText.setText(R.string.qibla_no_location);
            bearingText.setText("—°"); directionText.setText("Set location to calculate direction");
        }
    }

    private void renderHeading() {
        if (headingText != null) headingText.setText(hasHeading
                ? String.format(Locale.getDefault(), "%.0f°", azimuth) : "—°");
    }

    private void useDeviceLocation() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.ACCESS_FINE_LOCATION)
                !=PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
            statusText.setText(R.string.qibla_need_location_permission); return;
        }
        try {
            locationManager=(LocationManager)requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location best=null;
            for(String provider:locationManager.getProviders(true)) {
                Location candidate=locationManager.getLastKnownLocation(provider);
                if(candidate!=null && (best==null || candidate.getTime()>best.getTime())) best=candidate;
            }
            if(best!=null && System.currentTimeMillis()-best.getTime()<24L*60*60*1000) {
                setLocation(best.getLatitude(),best.getLongitude(),true);
                statusText.setText("Device location updated. Move in a figure-eight if the compass is inaccurate.");
                return;
            }
            requestFreshLocation();
        } catch (SecurityException | IllegalArgumentException e) {
            statusText.setText(R.string.qibla_location_unavailable);
        }
    }

    private void requestFreshLocation() {
        try {
            locationManager=(LocationManager)requireContext().getSystemService(Context.LOCATION_SERVICE);
            locationListener=new LocationListener() {
                @Override public void onLocationChanged(@NonNull Location loc) {
                    if (!isAdded()) return;
                    setLocation(loc.getLatitude(),loc.getLongitude(),true);
                    statusText.setText("Device location updated."); stopLocationRequest();
                }
                @Override public void onProviderDisabled(@NonNull String provider) { }
            };
            boolean requested=false;
            for(String provider:locationManager.getProviders(true)) {
                locationManager.requestLocationUpdates(provider,0,0,locationListener,Looper.getMainLooper());
                requested=true;
            }
            if(!requested) { statusText.setText(R.string.qibla_location_unavailable); return; }
            statusText.setText("Finding your location…");
            locationTimeout=()->{ stopLocationRequest(); if(isAdded()) statusText.setText(R.string.qibla_location_unavailable); };
            handler.postDelayed(locationTimeout,12000);
        } catch(SecurityException | IllegalArgumentException e) { statusText.setText(R.string.qibla_location_unavailable); }
    }

    private void stopLocationRequest() {
        if(locationTimeout!=null) handler.removeCallbacks(locationTimeout);
        if(locationManager!=null && locationListener!=null) {
            try { locationManager.removeUpdates(locationListener); } catch(RuntimeException ignored) { }
        }
        locationListener=null;
    }

    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,
                                                       @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==LOCATION_REQUEST) {
            boolean granted=false;
            for(int result:results) if(result==PackageManager.PERMISSION_GRANTED) granted=true;
            if(granted) useDeviceLocation(); else if(statusText!=null) statusText.setText(R.string.qibla_need_location_permission);
        }
    }

    @Override public void onResume() {
        super.onResume();
        sensors=(SensorManager)requireContext().getSystemService(Context.SENSOR_SERVICE);
        rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(rotationSensor!=null) sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_UI);
        else {
            accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer=sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if(accelerometer!=null) sensors.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_UI);
            if(magnetometer!=null) sensors.registerListener(this,magnetometer,SensorManager.SENSOR_DELAY_UI);
        }
        if(rotationSensor==null && (accelerometer==null || magnetometer==null) && statusText!=null)
            statusText.setText(R.string.qibla_heading_unavailable);
        else if(!hasHeading && statusText!=null) statusText.setText(R.string.qibla_calibrate_hint);
    }

    @Override public void onPause() { super.onPause(); if(sensors!=null)sensors.unregisterListener(this); stopLocationRequest(); }

    @Override public void onSensorChanged(SensorEvent e) {
        if(e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR) {
            float[] matrix=new float[9], orientation=new float[3];
            SensorManager.getRotationMatrixFromVector(matrix,e.values);
            applyDisplayRotation(matrix);
            SensorManager.getOrientation(matrix,orientation);
            updateHeading((float)Math.toDegrees(orientation[0]));
        } else if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER) {
            gravityValues=e.values.clone(); updateFallbackHeading();
        } else if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues=e.values.clone(); updateFallbackHeading();
        }
    }

    private void updateFallbackHeading() {
        if(gravityValues==null || magneticValues==null)return;
        float[] matrix=new float[9], orientation=new float[3];
        if(!SensorManager.getRotationMatrix(matrix,null,gravityValues,magneticValues))return;
        applyDisplayRotation(matrix); SensorManager.getOrientation(matrix,orientation);
        updateHeading((float)Math.toDegrees(orientation[0]));
    }

    private void applyDisplayRotation(float[] matrix) {
        Display display=requireActivity().getWindowManager().getDefaultDisplay();
        int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        switch(display.getRotation()) {
            case Surface.ROTATION_0: break;
            case Surface.ROTATION_90: x=SensorManager.AXIS_Y; y=SensorManager.AXIS_MINUS_X; break;
            case Surface.ROTATION_180: x=SensorManager.AXIS_MINUS_X; y=SensorManager.AXIS_MINUS_Y; break;
            case Surface.ROTATION_270: x=SensorManager.AXIS_MINUS_Y; y=SensorManager.AXIS_X; break;
        }
        float[] remapped=new float[9];
        if(SensorManager.remapCoordinateSystem(matrix,x,y,remapped)) System.arraycopy(remapped,0,matrix,0,9);
    }

    private void updateHeading(float raw) {
        rawAzimuth=raw;
        float normalized=((raw+declination)%360+360)%360;
        if(hasHeading) { float delta=((normalized-azimuth+540)%360)-180; azimuth=(azimuth+delta*.22f+360)%360; }
        else azimuth=normalized;
        hasHeading=true;
        if(compass!=null)compass.setAngles(azimuth,(float)bearing);
        if(headingText!=null)headingText.setText(String.format(Locale.getDefault(),"%.0f°",azimuth));
        renderBearingStatus();
    }

    private void renderBearingStatus() {
        if(statusText==null)return;
        if(!hasLocation) { statusText.setText(R.string.qibla_no_location); return; }
        if(!hasHeading) { statusText.setText(R.string.qibla_calibrate_hint); return; }
        if(lowAccuracy) { statusText.setText(R.string.qibla_needs_calibration); return; }
        float relative=((float)bearing-azimuth+540)%360-180;
        if(Math.abs(relative)<4)statusText.setText(R.string.qibla_aligned);
        else if(relative<0)statusText.setText(getString(R.string.qibla_turn_left,Math.abs(relative)));
        else statusText.setText(getString(R.string.qibla_turn_right,relative));
    }

    private void showCalibrationHelp() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Improve compass accuracy")
                .setMessage("Move your phone slowly in a figure-eight pattern. Keep it away from magnetic cases, speakers, and metal surfaces. Calibration is handled by your device’s compass sensor; this app cannot recalibrate the sensor directly.")
                .setPositiveButton(android.R.string.ok,null).show();
    }

    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {
        lowAccuracy=accuracy<SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
        renderBearingStatus();
    }
}
