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
            if(intent.hasExtra("error")) {
                showExitDialog(intent.getStringExtra("error"));
            }
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(myBroadcastReceiver, new IntentFilter(MyService.MY_SERVICE_BROADCAST_TAG));

        if(MyService.isRunning == false)
            startService(new Intent(this, MyService.class));

        bindService(new Intent(this, MyService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(myBroadcastReceiver);
        unbindService(serviceConnection);
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
        if(event.getAction() == MotionEvent.ACTION_DOWN)
            myService.startDetectorOfInOutdoor();
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