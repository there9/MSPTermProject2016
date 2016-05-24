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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener, SensorEventListener {
    final String ALARM_BROADCAST_TAG = "com.koreatech.cse.termproject";

    TextView summaryStepText, logText, totalStepTimeText, maximunLocationText;
    LocationManager locationManager;
    LocationProvider locationProvider;
    SensorManager sensorManager;
    WifiManager wifiManager;

    Sensor accelerometerSensor, lightSensor;

    // TODO 코드 정리 필요
    Toast toast;

    AlarmManager alarmManager;
    PendingIntent alarmPendingIntent;
    BroadcastReceiver alarmBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //wifiManager.startScan();
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
            //toast.cancel();
            toast.setText(isIndoor ? "실내" : "실외");
            toast.show();

            logText.setText(str);
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
                if(gpsSatellite.usedInFix())
                    str += gpsSatellite.toString() + "\n";
            }

            logText.setText(str);

            //toast.cancel();
            toast.setText("위성수: " +  count);
            //toast.setText((count > 5 ? "실외" : "실내") + " " + count);
            toast.show();
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
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

        // UI 관련
        summaryStepText = (TextView) findViewById(R.id.Summary_Step_Text);
        logText = (TextView) findViewById(R.id.logText);
        totalStepTimeText = (TextView) findViewById(R.id.TotalStep_Time_Text);
        maximunLocationText = (TextView) findViewById(R.id.Maximun_Location_Text);

        // TODO 코드 정리 필요
        logText.setMovementMethod(new ScrollingMovementMethod());

        toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);

        final Intent alramIntent = new Intent(ALARM_BROADCAST_TAG);
        alarmPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alramIntent, 0);

        Intent wifiIntent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, wifiIntent, 0);

    }

    protected void onDestory() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);

        // TODO 코드 정리 필요
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, 5000, alarmPendingIntent);

        IntentFilter alarmFilter = new IntentFilter(ALARM_BROADCAST_TAG);
        registerReceiver(alarmBroadcastReceiver, alarmFilter);

        //IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        //registerReceiver(wifiBroadcastReceiver, wifiFilter);

        //wifiManager.startScan();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.addGpsStatusListener(gpsStatuslistener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
    }
    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);

        // TODO 코드 정리 필요
        unregisterReceiver(alarmBroadcastReceiver);
        alarmManager.cancel(alarmPendingIntent);

        //unregisterReceiver(wifiBroadcastReceiver);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeGpsStatusListener(gpsStatuslistener);
        locationManager.removeUpdates(locationListener);
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
