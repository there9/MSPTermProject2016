package com.koreatech.cse.termproject;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {
    // UI 관련
    TextView todayText;
    TextView summaryStepText;
    TextView totalStepTimeText;
    TextView maximumLocationText;
    TextView logText;
    ListView logList;

    int step = 0;

    Location lastLocation;

    ArrayAdapter<String> logListAdaptor;

    // 로그 관련
    private String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/log.txt";
    private PrintWriter logWriter;
    private Date beforeDate = new Date();

    // 서비스와 통신 관련
    MyService myService;
    private boolean isBound;
    BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logText.setText("");
            if(intent.hasExtra("location")) {
                lastLocation = intent.getParcelableExtra("location");
                logText.append(lastLocation.getLatitude() + " / " + lastLocation.getLongitude() + "\n" +
                        "운동장: " + intent.getFloatExtra("ground", -1) + "m\n" +
                        "본부: " + intent.getFloatExtra("main", -1) + "m\n" +
                        "4공: " + intent.getFloatExtra("comgong", -1) + "m\n" +
                        "---------\n" +
                        "오차: " + lastLocation.getAccuracy() + "m\n" +
                        "---------\n");
            }
            if(intent.hasExtra("error")) {
                showExitDialog(intent.getStringExtra("error"));
            }
            logText.append("걸음수: " + StepMonitor.step);
            readUpdateLog();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 관련
        todayText = (TextView) findViewById(R.id.todayTextView);
        summaryStepText = (TextView) findViewById(R.id.summaryStepText);
        totalStepTimeText = (TextView) findViewById(R.id.totalStepTimeText);
        maximumLocationText = (TextView) findViewById(R.id.maximunLocationText);
        logText = (TextView) findViewById(R.id.logText);
        logList = (ListView) findViewById(R.id.logList);
        logListAdaptor = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

        todayText.setText((new SimpleDateFormat("yyyy년 M월 dd일", java.util.Locale.getDefault()).format(new Date())));
        logText.setMovementMethod(new ScrollingMovementMethod());
        logList.setAdapter(logListAdaptor);

        readUpdateLog();

        if(MyService.isRunning == false)
            startService(new Intent(this, MyService.class));

        bindService(new Intent(this, MyService.class), serviceConnection, Context.BIND_AUTO_CREATE);

        startService(new Intent(this, StepMonitor.class));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(serviceConnection);
        stopService(new Intent(this, StepMonitor.class));
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(myBroadcastReceiver, new IntentFilter(MyService.MY_SERVICE_BROADCAST_TAG));
    }
    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(myBroadcastReceiver);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        // 서비스와 연결되면
        public void onServiceConnected(ComponentName name, IBinder service) {
            myService = ((MyService.ServiceBinder)service).getService();
            isBound = true;
        }
        @Override
        // 서비스와 연결이 해제되면
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    public void onClick(View v) {

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            //myService.startDetectorOfInOutdoor();
            myService.stopDetectOutdoorLocation();
            myService.startDetectOutdoorLocation();
        }
        return false;
    }

    public void readUpdateLog() {
        logListAdaptor.clear();

        try {
            String buffer = "";

            FileInputStream file = new FileInputStream(logPath);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
            while ((buffer = bufferedReader.readLine()) != null) {
                logListAdaptor.add(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showExitDialog(String msg) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(msg);
        alert.setNegativeButton("확인", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                System.exit(1);
            }
        });
        alert.show();
    }


}