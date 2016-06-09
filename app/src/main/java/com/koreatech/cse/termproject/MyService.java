package com.koreatech.cse.termproject;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MyService extends Service {
    public final static String MY_SERVICE_BROADCAST_TAG = "com.koreatech.cse.termproject.service";
    private final static String ALARM_BROADCAST_TAG = "com.koreatech.cse.termproject.alarm";

    public static boolean isRunning;

    // 센서 관련
    private LocationManager locationManager;
    private LocationProvider locationProvider;
    private WifiManager wifiManager;

    // GPS 관련
    private long gpsStartTime;
    private final int GPS_WAIT_MILLIS = 8000;

    private double latitude;
    private double longitude;

    // 정해진 위치 정보
    private Location sportGroundLocation;
    private Location universityMainLocation;

    // 로그 관련
    private String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/log.txt";
    private String logSummaryPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/summary.txt";

    private PrintWriter logWriter;
    private PrintWriter logSummaryWriter;
    private Date beforeDate = new Date();

    // Alarm broadcast
    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;
    private BroadcastReceiver alarmBroadcastReceiver;

    // StepMonitor broadcast
    private BroadcastReceiver stepBroadcastReceiver;

    // Wifi scan broadcast
    private BroadcastReceiver wifiBroadcastReceiver;

    // GPS status listener
    private GpsStatus.Listener gpsStatusListener;
    private LocationListener locationListener;


    public MyService() {

    }

    @Override
    public void onCreate() {
        // 센서 관련
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false)
            showExitDialog("GPS를 사용할 수 없습니다.");
        if(wifiManager.isWifiEnabled() == false)
            showExitDialog("와이파이를 사용할 수 없습니다.");

        // 로그 관련
        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logPath, false)));
            logSummaryWriter = new PrintWriter(new BufferedWriter(new FileWriter(logSummaryPath, false)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 정해진 위치 정보
        sportGroundLocation = new Location("");
        universityMainLocation = new Location("");

        sportGroundLocation.setLatitude(36.762581);
        sportGroundLocation.setLongitude(127.284527);
        universityMainLocation.setLatitude(36.764215);
        universityMainLocation.setLongitude(127.282173);

        // Alarm broadcast
        alarmPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ALARM_BROADCAST_TAG), 0);
        initAlarm();

        isRunning = true;
    }

    @Override
    public void onDestroy() {
        stopStepMonitor();
        stopDetectorOfInOutdoor();

        logWriter.close();
        logSummaryWriter.close();
        isRunning = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    public class ServiceBinder extends Binder {
        MyService getService() {
            return MyService.this;
        }
    }

    private void showExitDialog(String msg) {
        Intent intent = new Intent(MY_SERVICE_BROADCAST_TAG);
        intent.putExtra("error", msg);

        sendBroadcast(intent);
    }

    public void startStepMonitor() {
        if(stepBroadcastReceiver != null)
            return;

        stepBroadcastReceiver = new StepBroadcastReceiver();

        registerReceiver(stepBroadcastReceiver, new IntentFilter(StepMonitor.STEP_BROADCAST_TAG));
        startService(new Intent(this, StepMonitor.class));
    }
    private void stopStepMonitor() {
        if(stepBroadcastReceiver == null)
            return;

        unregisterReceiver(stepBroadcastReceiver);
        stopService(new Intent(this, StepMonitor.class));

        stepBroadcastReceiver = null;
    }

    public void startDetectorOfInOutdoor() {
        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false)
            showExitDialog("GPS를 사용할 수 없습니다.");
        if(wifiManager.isWifiEnabled() == false)
            showExitDialog("와이파이를 사용할 수 없습니다.");

        if(locationListener != null || wifiBroadcastReceiver != null)
            return;

        Toast.makeText(getApplicationContext(), "탐지시작", Toast.LENGTH_SHORT).show();
        // NOTE GPS 먼저 검색을 시작함
        startGpsScan();
    }
    public void stopDetectorOfInOutdoor() {
        if(gpsStatusListener == null && wifiBroadcastReceiver == null)
            return;

        Toast.makeText(getApplicationContext(), "탐지중지", Toast.LENGTH_SHORT).show();

        cleanAlarm();       // alarm clean
        stopWifiScan();     // wifi stop
        stopGpsScan();      // GPS stop
    }

    private void initAlarm() {
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, 5000, alarmPendingIntent);
    }
    public void cleanAlarm() {
        if(alarmBroadcastReceiver == null)
            return;

        unregisterReceiver(alarmBroadcastReceiver);
        alarmManager.cancel(alarmPendingIntent);

        alarmBroadcastReceiver = null;
    }

    public void startGpsScan() {
        gpsStartTime = System.currentTimeMillis();

        if(gpsStatusListener != null)
            return;

        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false)
            showExitDialog("GPS를 사용할 수 없습니다.");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        gpsStatusListener = new GpsStatusListener();
        locationListener = new LocationListener();

        locationManager.addGpsStatusListener(gpsStatusListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
    }
    public void stopGpsScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if(locationListener == null)
            return;

        locationManager.removeGpsStatusListener(gpsStatusListener);
        locationManager.removeUpdates(locationListener);

        gpsStatusListener = null;
        locationListener = null;
    }

    public void startWifiScan() {
        if(wifiBroadcastReceiver != null)
            return;

        if(wifiManager.isWifiEnabled() == false)
            showExitDialog("와이파이를 사용할 수 없습니다.");

        alarmBroadcastReceiver = new AlarmBroadcastReceiver();
        wifiBroadcastReceiver = new WifiBroadcastReceiver();

        registerReceiver(alarmBroadcastReceiver, new IntentFilter(ALARM_BROADCAST_TAG));
        registerReceiver(wifiBroadcastReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        wifiManager.startScan();
    }
    public void stopWifiScan() {
        if(wifiBroadcastReceiver == null)
            return;

        unregisterReceiver(wifiBroadcastReceiver);
        wifiBroadcastReceiver = null;
    }

    public void appendLog(String msg) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        long distantTime = (date.getTime() - writeDate.getTime()) /  60000;
        if(isMoving==true) {
            MainActivity.locationInfo.totalSumMovingTime(distantTime);
        }
        else
        {
            MainActivity.locationInfo.timeCompare(distantTime, indoorLocationName);
        }
        try {
            logSummaryWriter = new PrintWriter(new BufferedWriter(new FileWriter(logSummaryPath, false)));

        msg = "["+ dateFormat.format(writeDate)+"-"+ dateFormat.format(date) + "] " + distantTime + "분 " + msg;
        logSummaryWriter.write(LocationInfo.totalMovingTime + "\n" + LocationInfo.totalStepCount + "\n" + MainActivity.locationInfo.locationName + "\n" + MainActivity.locationInfo.time);
        logSummaryWriter.flush();

        logWriter.println(msg);
        logWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        beforeDate = new Date();

        sendBroadcast(new Intent(MY_SERVICE_BROADCAST_TAG));
    }

    class AlarmBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiManager.startScan();
        }
    }
    int totalStepCount;
    String indoorString;
    Date writeDate;
    boolean isMoving = false;
    class StepBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            totalStepCount = intent.getIntExtra("steps", 0);
            writeDate = new Date();
            writeDate.setTime(intent.getLongExtra("currentDate", 0));
            isMoving = intent.getBooleanExtra("isMoving",true);
            isMoving = intent.getBooleanExtra("isMoving",true);
            if(isMoving==false)
            {
                indoorString = "정지";
                startDetectorOfInOutdoor();
            }
            else
            {
                LocationInfo.totalSumStep(totalStepCount);
                indoorString = "이동 "+totalStepCount +"걸음";
                startDetectorOfInOutdoor();
                stopStepMonitor();

            }
        }
    }
    String indoorLocationName = "";
    class WifiBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> scanResultList;
            scanResultList = wifiManager.getScanResults();

            Toast.makeText(getApplicationContext(), "와이파이 스켄..", Toast.LENGTH_SHORT).show();

            Collections.sort(scanResultList, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult lhs, ScanResult rhs) {
                    return rhs.level - lhs.level;
                }
            });

            //boolean isIndoor = false;
            indoorLocationName = "실내";
            String str = "";

            for (ScanResult scanResult : scanResultList) {
                str += scanResult.SSID + "\n";
                str += "  BSSID: " + scanResult.BSSID + "\n";
                str += "  Level: " + scanResult.level + "\n\n";

                // MCM랩
                if (scanResult.BSSID.equalsIgnoreCase("64:e5:99:23:d3:a4")) {
                    if (scanResult.level > -55) {
                        //isIndoor = true;
                        indoorLocationName = "MCM랩";
                        //break;
                    }
                }

                // A312
                // NSTL 2.4GHz
                if (scanResult.BSSID.equalsIgnoreCase("00:26:66:cc:e3:8c")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        indoorLocationName = "A312";
                        //break;
                    }
                }
                // iptime
                if (scanResult.BSSID.equalsIgnoreCase("90:9f:33:cd:28:62")) {
                    if (scanResult.level > -60) {
                        //isIndoor = true;
                        indoorLocationName = "A312";
                        //break;
                    }
                }
            }

            //logText.setText((isIndoor ? "현재위치: MCN랩" : "현재위치: 모르는 실내") + "\n" + str);

            // NOTE 이미 이 단계까지 왔다는건 GPS(실외) 판단이 실패하여 넘어왔으므로 실내라고 판단함
            appendLog(indoorString +" "+indoorLocationName);

            Log.d("WIFI", str);
            stopDetectorOfInOutdoor();
            startStepMonitor();
        }
    }

    class GpsStatusListener implements GpsStatus.Listener {
        @Override
        public void onGpsStatusChanged(int event) {
            if(event != GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                return;

            GpsStatus gpsStatus = locationManager.getGpsStatus(null);
            Iterable<GpsSatellite> gpsSatellites = gpsStatus.getSatellites();

            int count = 0;
            String str = "";
            for(GpsSatellite gpsSatellite : gpsSatellites) {
                if(gpsSatellite.usedInFix()) {
                    count++;
                    //str += "[" + count + "] " + gpsSatellite.toString() + "\n";
                }
            }

            if(count > 5) {
                //logText.setText("GPS>> 실외판정됨.");
                indoorLocationName = "실외";
                appendLog(indoorString + " "+indoorLocationName);
                stopDetectorOfInOutdoor();
                startStepMonitor();
            } else {
                if(System.currentTimeMillis() - gpsStartTime < GPS_WAIT_MILLIS) {
                    return;
                }
                // 실내
                Toast.makeText(getApplicationContext(), "GPS 꺼짐", Toast.LENGTH_SHORT).show();
                stopGpsScan();
                startWifiScan();
            }

            //Toast.makeText(getApplicationContext(), count, Toast.LENGTH_SHORT).show();

            //logText.setText(Integer.toString(count));
        }
    }

    class LocationListener implements android.location.LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }
        @Override
        public void onProviderEnabled(String provider) { }
        @Override
        public void onProviderDisabled(String provider) {
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) == false)
                showExitDialog("GPS를 사용할 수 없습니다.");
        }
    }
}
