package com.koreatech.cse.termproject;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MyService extends Service {
    public final static String MY_SERVICE_BROADCAST_TAG = "com.koreatech.cse.termproject.service";
    private final static String ALARM_BROADCAST_TAG = "com.koreatech.cse.termproject.alarm";
    private final static String PROXIMITY_BROADCAST_TAG = "com.koreatech.cse.termproject.proximity";
    public final static String DETECT_OUTDOOR_BROADCAST_TAG = "com.koreatech.cse.termproject.detect_outdoor";

    public static boolean isRunning;

    // 센서 관련
    private LocationManager locationManager;
    private LocationProvider locationProvider;
    private WifiManager wifiManager;

    // GPS 관련
    private long gpsStartTime;
    private final int GPS_WAIT_MILLIS = 10000;

    // 정해진 위치 정보
    private Location sportGroundLocation;
    private Location universityMainLocation;
    private Location comgongLocation;

    // 로그 관련
    private String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/log.txt";
    private String logSummaryPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/summary.txt";

    private PrintWriter logWriter;
    private PrintWriter logSummaryWriter;
    private Date beforeDate = new Date();

    // 근접 경보
    private LocationListener locationListenerByProximity;


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
        sportGroundLocation = new Location(LocationManager.GPS_PROVIDER);
        universityMainLocation = new Location(LocationManager.GPS_PROVIDER);
        comgongLocation = new Location(LocationManager.GPS_PROVIDER);

        sportGroundLocation.setLatitude(36.762581);
        sportGroundLocation.setLongitude(127.284527);

        universityMainLocation.setLatitude(36.764215);
        universityMainLocation.setLongitude(127.282173);

        comgongLocation.setLatitude(36.761369);
        comgongLocation.setLongitude(127.280265);

        // Alarm broadcast
        alarmPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ALARM_BROADCAST_TAG), 0);
        initAlarm();

        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        StepMonitor.steps = 0;
        StepMonitor.totalStepCount = 0;
        StepMonitor.currentDate = new Date();

        LocationInfo.totalStepCount = 0;
        LocationInfo.totalMovingTime = 0;

        startStepMonitor();

        Toast.makeText(getApplicationContext(), "서비스 시작됨", Toast.LENGTH_SHORT).show();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopStepMonitor();
        stopDetectorOfInOutdoor();

        Toast.makeText(getApplicationContext(), "서비스 종료됨", Toast.LENGTH_SHORT).show();

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

    public void startDetectOutdoorLocation() {
        locationListenerByProximity = new LocationListener();

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 1, locationListenerByProximity);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
    }
    public void stopDetectOutdoorLocation() {
        if(locationListenerByProximity == null)
            return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(locationListenerByProximity);
        locationListenerByProximity = null;
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

        knownOutdoorLocation = "";

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
    int savestep = 0;
    public void stopWifiScan() {
        if(wifiBroadcastReceiver == null)
            return;

        unregisterReceiver(wifiBroadcastReceiver);
        wifiBroadcastReceiver = null;
    }

    public void appendLog(String msg, int step) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        long distantTime = (date.getTime() - writeDate.getTime()) / 60000;
        if (isContinueMoving == true) {
            StepMonitor.currentDate = new Date();
        }
        Log.d("a", distantTime + "");
        if (distantTime > 0) {
            LocationInfo.totalSumStep(step);
            movingTotalCount = 0;
            if (isMoving == true || isContinueMoving == true) {
                MainActivity.locationInfo.totalSumMovingTime(distantTime);
            }
            MainActivity.locationInfo.timeCompare(distantTime, indoorLocationName);

            try {
                logSummaryWriter = new PrintWriter(new BufferedWriter(new FileWriter(logSummaryPath, false)));

                msg = "[" + dateFormat.format(writeDate) + "-" + dateFormat.format(date) + "] " + distantTime + "분 " + msg;
                logSummaryWriter.write(LocationInfo.totalMovingTime + "\n" + LocationInfo.totalStepCount + "\n" + MainActivity.locationInfo.locationName + "\n" + MainActivity.locationInfo.time);
                logSummaryWriter.flush();

                logWriter.println(msg);
                logWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            beforeDate = new Date();
            savestep = 0;
            sendBroadcast(new Intent(MY_SERVICE_BROADCAST_TAG));
        }
        else
        {
            savestep += step;
        }
    }

    class AlarmBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiManager.startScan();
        }
    }
    int localStepCount;
    int movingTotalCount = 0;
    String indoorString;
    Date writeDate;
    boolean isMoving = false;
    boolean isContinueMoving = false;
    class StepBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 계속 이동중인 상황"
            if(intent.hasExtra("continue_moving") && intent.getBooleanExtra("continue_moving", false) == true) {
                if(isContinueMoving == false)
                    writeDate = new Date();

                isContinueMoving = true;

                movingTotalCount += localStepCount;
                localStepCount = intent.getIntExtra("steps", 0);

                startDetectorOfInOutdoor();
            } else {
                isContinueMoving = false;

                movingTotalCount += localStepCount;
                localStepCount = intent.getIntExtra("steps", 0);
                writeDate = new Date();
                writeDate.setTime(intent.getLongExtra("currentDate", 0));
                isMoving = intent.getBooleanExtra("isMoving", true);
                if (isMoving == false) {
                    indoorString = "정지";
                    startDetectorOfInOutdoor();
                } else {
                    //LocationInfo.totalSumStep(localStepCount);
                    //indoorString = "이동 " + movingTotalCount + "걸음";
                    startDetectorOfInOutdoor();
                    stopStepMonitor();

                }
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


            String tmpLocationName = "실내";
            //boolean isIndoor = false;
            String str = "";
            for (ScanResult scanResult : scanResultList) {
                str += scanResult.SSID + "\n";
                str += "  BSSID: " + scanResult.BSSID + "\n";
                str += "  Level: " + scanResult.level + "\n\n";

                // MCM랩
                /*if (scanResult.BSSID.equalsIgnoreCase("64:e5:99:23:d3:a4")) {
                    if (scanResult.level > -55) {
                        //isIndoor = true;
                        tmpLocationName = "MCM랩";
                        //break;
                    }
                }*/

                // A312
                // NSTL 2.4GHz
                if (scanResult.BSSID.equalsIgnoreCase("00:26:66:cc:e3:8c")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "A312";
                        //break;
                    }
                }
                // iptime
                if (scanResult.BSSID.equalsIgnoreCase("90:9f:33:cd:28:62")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "A312";
                        //break;
                    }
                }
                // KUTAP
                if (scanResult.BSSID.equalsIgnoreCase("50:1c:bf:41:cf:20")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "A312";
                        //break;
                    }
                }
                // KUTAP_N
                if (scanResult.BSSID.equalsIgnoreCase("50:1c:bf:41:cf:21")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "A312";
                        //break;
                    }
                }

                // 다산
                // KUTAP_N
                if (scanResult.BSSID.equalsIgnoreCase("20:3a:07:9e:a6:ce")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
                // KUTAP
                if (scanResult.BSSID.equalsIgnoreCase("20:3a:07:9e:a6:cf")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
                // KUTAP
                if (scanResult.BSSID.equalsIgnoreCase("20:3a:07:9e:a6:c0")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
                // KUTAP
                if (scanResult.BSSID.equalsIgnoreCase("a4:18:75:58:77:d0")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
                // KUTAP_N
                if (scanResult.BSSID.equalsIgnoreCase("20:3a:07:9e:a6:c1")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
                // KUTAP_N
                if (scanResult.BSSID.equalsIgnoreCase("20:3a:07:49:5c:ee")) {
                    if (scanResult.level > -65) {
                        //isIndoor = true;
                        tmpLocationName = "다산";
                        //break;
                    }
                }
            }

            Toast.makeText(getApplicationContext(),tmpLocationName+" "+indoorLocationName,Toast.LENGTH_SHORT).show();
            //logText.setText((isIndoor ? "현재위치: MCN랩" : "현재위치: 모르는 실내") + "\n" + str);

            // NOTE 이미 이 단계까지 왔다는건 GPS(실외) 판단이 실패하여 넘어왔으므로 실내라고 판단함

            if (isMoving == false) {
                indoorString = "정지";
            } else {
                indoorString = "이동 " + localStepCount + "걸음";
            }

            if(isContinueMoving == false) {
                indoorLocationName = tmpLocationName;
                if(isMoving==true&&(indoorLocationName =="실외"||indoorLocationName =="실내"))
                {
                    appendLog(indoorString + " ", localStepCount);
                }
                else
                {
                    appendLog(indoorString + " " + indoorLocationName, localStepCount);
                }
            } else {
                // 끊임없이 이동중 - 다른 장소로 왔다
                if (indoorLocationName.equals(tmpLocationName) == false) {

                    //LocationInfo.totalSumStep(localStepCount);
                    if(indoorLocationName.isEmpty()==false)
                    {
                        indoorString = "이동 " + localStepCount + "걸음";
                        if(indoorLocationName !="실내"&&indoorLocationName !="실외")
                        {
                            appendLog(indoorString + " " + indoorLocationName, localStepCount);
                            StepMonitor.totalStepCount = savestep;
                            writeDate = new Date();
                        }
                        else
                        {
                            if(tmpLocationName !="실내"&&tmpLocationName!="실외" )
                            {
                                appendLog(indoorString + " " + tmpLocationName, localStepCount);
                            }
                            StepMonitor.totalStepCount = savestep;
                            writeDate = new Date();
                        }

                    }

                    indoorLocationName = tmpLocationName;

                }
            }
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
                /*String tmpLocationName;
                if(knownOutdoorLocation.isEmpty())
                    tmpLocationName = "실외";
                else
                    tmpLocationName = knownOutdoorLocation;

                if(isContinueMoving == false) {
                    indoorLocationName = tmpLocationName;
                    appendLog(indoorString + " " + indoorLocationName);
                } else {
                    if(indoorLocationName.equals(tmpLocationName) == false) {
                        indoorString = "이동 " + localStepCount + "걸음";
                        if(!indoorLocationName.isEmpty()) {
                            appendLog(indoorString + " " + tmpLocationName);

                            StepMonitor.totalStepCount = 0;
                        }
                        indoorLocationName = tmpLocationName;
                        writeDate = new Date();
                    }
                }
                stopDetectorOfInOutdoor();*/
                //startStepMonitor();
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

    String knownOutdoorLocation = "";
    class LocationListener implements android.location.LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            Intent intent = new Intent(MY_SERVICE_BROADCAST_TAG);
            intent.putExtra("location", location);
            intent.putExtra("ground", location.distanceTo(sportGroundLocation));
            intent.putExtra("main", location.distanceTo(universityMainLocation));
            intent.putExtra("comgong", location.distanceTo(comgongLocation));
            knownOutdoorLocation = "실외";
            // 운동장 거리
            if(location.distanceTo(sportGroundLocation) < 80)
                knownOutdoorLocation = "운동장";
            // 대학본부
            if(location.distanceTo(universityMainLocation) < 50)
                knownOutdoorLocation = "대학본부";
            // 4공
            /*if(location.distanceTo(comgongLocation) < 20)
                knownOutdoorLocation = "4공학관";*/

            Toast.makeText(getApplicationContext(), knownOutdoorLocation + " / " + location.getAccuracy(), Toast.LENGTH_SHORT).show();

            sendBroadcast(intent);

            if(location.getAccuracy() < 100) {
                String tmpLocationName = knownOutdoorLocation;


                if (isMoving == false) {
                    indoorString = "정지";
                } else {
                    indoorString = "이동 " + localStepCount + "걸음";
                }

                if(isContinueMoving == false) {
                    indoorLocationName = tmpLocationName;

                    if(isMoving==true&&(indoorLocationName =="실외"||indoorLocationName =="실내"))
                    {
                        appendLog(indoorString + " ", localStepCount);
                    }
                    else
                    {
                        appendLog(indoorString + " " + indoorLocationName, localStepCount);
                    }
                } else {
                    // 끊임없이 이동중 - 다른 장소로 왔다
                    if (indoorLocationName.equals(tmpLocationName) == false) {

                        //LocationInfo.totalSumStep(localStepCount);
                        if(indoorLocationName.isEmpty()==false)
                        {
                            indoorString = "이동 " + localStepCount + "걸음";
                            if(indoorLocationName !="실내"&&indoorLocationName !="실외")
                            {
                                appendLog(indoorString + " " + indoorLocationName, localStepCount);
                                StepMonitor.totalStepCount = savestep;
                                writeDate = new Date();
                            }
                            else
                            {
                                if(tmpLocationName !="실내"&&tmpLocationName!="실외" )
                                {
                                    appendLog(indoorString + " " + tmpLocationName, localStepCount);
                                    StepMonitor.totalStepCount = savestep;
                                    writeDate = new Date();
                                }
                            }
                        }

                        indoorLocationName = tmpLocationName;

                    }
                }

                stopDetectorOfInOutdoor();
                startStepMonitor();
            }
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