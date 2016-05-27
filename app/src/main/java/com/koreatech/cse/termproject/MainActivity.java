package com.koreatech.cse.termproject;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener, SensorEventListener {
    final String ALARM_BROADCAST_TAG = "com.koreatech.cse.termproject";

    // UI 관련
    TextView todayText;
    TextView summaryStepText;
    TextView totalStepTimeText;
    TextView maximunLocationText;
    TextView logText;

    // 센서 관련
    LocationManager locationManager;
    LocationProvider locationProvider;
    SensorManager sensorManager;
    Sensor accelerometerSensor;
    WifiManager wifiManager;

    // 정해진 위치 정보
    Location sportGroundLocation;
    Location universityMainLocation;

    // TODO 코드 정리 필요
    Toast toast;

    AlarmManager alarmManager;
    PendingIntent alarmPendingIntent;
    BroadcastReceiver alarmBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiManager.startScan();
        }
    };

    PendingIntent wifiPendingIntent;
    BroadcastReceiver wifiBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResultList;
            scanResultList = wifiManager.getScanResults();

            Collections.sort(scanResultList, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult lhs, ScanResult rhs) {
                    return rhs.level - lhs.level;
                }
            });

            boolean isIndoor = false;
            String str = "";
            for (ScanResult scanResult : scanResultList) {
                str += scanResult.SSID + "\n";
                str += "  BSSID: " + scanResult.BSSID + "\n";
                str += "  Level: " + scanResult.level + "\n\n";
                if (scanResult.BSSID.equalsIgnoreCase("64:e5:99:23:d3:a4")) {
                    if (scanResult.level > -50) {
                        isIndoor = true;
                    }
                }
            }

            logText.setText(isIndoor ? "실내" : "실외");
        }
    };

    GpsStatus.Listener gpsStatuslistener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            if(event != GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                return;

            GpsStatus gpsStatus = locationManager.getGpsStatus(null);
            Iterable<GpsSatellite> gpsSatellites = gpsStatus.getSatellites();

            int count = 0;
            String str = "";
            for(GpsSatellite gpsSatellite : gpsSatellites) {
                count++;
                //if(gpsSatellite.usedInFix())
                    str += gpsSatellite.toString() + "\n";
            }

            logText.setText(str);
            //logText.setText("위성수: " +  count);
            //toast.setText((count > 5 ? "실외" : "실내") + " " + count);
            //toast.show();
        }
    };

    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 센서 관련
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


        // UI 관련
        todayText = (TextView) findViewById(R.id.todayTextView);
        summaryStepText = (TextView) findViewById(R.id.summaryStepText);
        totalStepTimeText = (TextView) findViewById(R.id.totalStepTimeText);
        maximunLocationText = (TextView) findViewById(R.id.maximunLocationText);
        logText = (TextView) findViewById(R.id.logText);

        todayText.setText((new SimpleDateFormat("yyyy년 M월 dd일", java.util.Locale.getDefault()).format(new Date())));
        logText.setMovementMethod(new ScrollingMovementMethod());


        // 정해진 위치 정보
        sportGroundLocation = new Location("");
        universityMainLocation = new Location("");

        sportGroundLocation.setLatitude(36.762581);
        sportGroundLocation.setLongitude(127.284527);
        universityMainLocation.setLatitude(36.764215);
        universityMainLocation.setLongitude(127.282173);


        // TODO 코드 정리 필요
        toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);

        final Intent alarmIntent = new Intent(ALARM_BROADCAST_TAG);
        alarmPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alarmIntent, 0);

        Intent wifiIntent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, wifiIntent, 0);
    }

    protected void onDestory() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);


        // TODO 코드 정리 필요
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, 5000, alarmPendingIntent);

        IntentFilter alarmFilter = new IntentFilter(ALARM_BROADCAST_TAG);
        registerReceiver(alarmBroadcastReceiver, alarmFilter);

        IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiBroadcastReceiver, wifiFilter);

        wifiManager.startScan();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //locationManager.addGpsStatusListener(gpsStatuslistener);
        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
    }
    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);


        // TODO 코드 정리 필요
        unregisterReceiver(alarmBroadcastReceiver);
        alarmManager.cancel(alarmPendingIntent);

        unregisterReceiver(wifiBroadcastReceiver);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //locationManager.removeGpsStatusListener(gpsStatuslistener);
        //locationManager.removeUpdates(locationListener);
    }

    @Override
    public void onClick(View v) {

    }


    //SensorListener
    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
