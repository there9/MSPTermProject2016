package com.koreatech.cse.termproject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class StepMonitor extends Service implements SensorEventListener {
    public final static String STEP_BROADCAST_TAG = "com.koreatech.cse.termproject.step_scan";
    private final String LOGTAG = "StepMonitor";

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    long prevPickTime = 0;              // 이전 가속도 센서 검사 시간
    long curPickTime = 0;               // 현재 가속도 센서 검사 시간
    long timeSum = 0;                   // 가속도 센서 검사 시간의 합

    int stepScanTime = 3000;            // 스텝을 누적하고자하는 시간 (ms)
    long timeDifference = 65000000;     // ns = 65ms = 1000ms
    ArrayList<Double> RMS = new ArrayList<>();  // RMS 리스트

    private static final int MINUTE_PER_MAXIMUN_STEP = 15; //20
    private static final int ONE_MINUTE_COUNT = 800; ///800
    private static final int STOP_COUNT_FIVE = 4;
    int stopCount = 0;
    private int minute_count=0;
    Intent intent = new Intent(StepMonitor.STEP_BROADCAST_TAG);
    private int totalStepCount = 0;
    private boolean isMoving =false;
    Date currentDate;

    public static int steps = 0;

    // 로그 관련
    private String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/step.txt";
    private PrintWriter logWriter;
    private Date beforeDate = new Date();


    @Override
    public void onCreate() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "step");
        wakeLock.acquire();

        // 로그 관련
        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logPath, true)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        sensorManager.unregisterListener(this);

        if(wakeLock.isHeld())
            wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double avgRMS = 0;
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            curPickTime = event.timestamp;
            // 가속도 측정 시간 누적
            timeSum += (curPickTime - prevPickTime) / 1000000;
            prevPickTime = curPickTime;
            //Log.d("a", "" + minute_count + "" + stopCount + " " + isMoving);
            if(stopCount>STOP_COUNT_FIVE)
            {
                if(steps < MINUTE_PER_MAXIMUN_STEP&& steps>0)
                {
                    stopCount = 0;
                        intent.putExtra("isMoving", false);
                intent.putExtra("steps", (int) totalStepCount);
                intent.putExtra("currentDate", currentDate.getTime());
                sendBroadcast(intent);
                }
            }
            if(minute_count>ONE_MINUTE_COUNT) {
                Log.d("a", "" + stopCount + " " + isMoving);
                totalStepCount += steps;

                if (steps > MINUTE_PER_MAXIMUN_STEP && isMoving==false ) {
                    currentDate = new Date();
                    isMoving = true;
                    stopCount = 0;
                }
                else if(steps < MINUTE_PER_MAXIMUN_STEP && isMoving==false) {
                    if (stopCount == 0)
                    {
                        currentDate = new Date();
                    }
                    totalStepCount = 0;
                    stopCount++;
                }
                if(totalStepCount > MINUTE_PER_MAXIMUN_STEP && isMoving==true)
                {
                    if(steps < MINUTE_PER_MAXIMUN_STEP)
                    {
                        Date nowDate = new Date();
                        intent.putExtra("currentDate", currentDate.getTime());
                        intent.putExtra("isMoving", true);
                        intent.putExtra("steps", (int) totalStepCount);
                        totalStepCount = 0;
                        sendBroadcast(intent);
                        isMoving = false;
                    }
                    stopCount = 0;
                }

                steps =0;
                minute_count = 0;
            }
            minute_count++;
            // 순간 RMS 추가
            RMS.add(Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]));

            // 가속도 측정(RMS를 누적한) 시간이 스텝 측정시간을 넘었다면
            // 3초동안 RMS 검사를 함.
            if(timeSum > stepScanTime) {
                Log.d(LOGTAG, stepScanTime + "ms post..");

                // RMS 평균 구하기
                for(double e: RMS) {
                    appendLog(Double.toString(e));
                    avgRMS += e;
                }
                avgRMS /= RMS.size();

                // RMS평균이 4.5를 넘었다면 (기준치)
                if(avgRMS > 4.5) {
                    double localCount = 0;
                    double localMax = 0;

                    for (double e : RMS) {
                        // local 영역 5개 검사
                        if (localCount < 5) {
                            localMax = e > localMax ? e : localMax;
                            localCount++;
                        } else {
                            // 5개 검사 후 local영역 최대값이 RMS평균을 넘는다면 걸음으로 체크
                            if (localMax > avgRMS)
                                steps++;

                            localMax = 0;
                            localCount = 0;
                        }
                    }
                }

                appendLog("*** AVG : " + avgRMS);
                appendLog("***>>>>>>>> STEP : " + steps);

                Log.d(LOGTAG, "" + timeSum);
                Log.d(LOGTAG, Double.toString(avgRMS));
                Log.d(LOGTAG, ""+minute_count);
                Log.d(LOGTAG, ">>>STEP: " + Double.toString(steps));

                sendBroadcast(new Intent(MyService.MY_SERVICE_BROADCAST_TAG));

                RMS.clear();
                timeSum = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    public void appendLog(String msg) {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        long distantTime = (date.getTime() - date.getTime()) / 1000 / 60;

        msg = "[" + dateFormat.format(date) + "] " + msg;

        logWriter.println(msg);
        logWriter.flush();
        beforeDate = new Date();
    }
}
