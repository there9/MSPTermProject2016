package com.koreatech.cse.termproject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Date;

public class StepMonitor extends Service implements SensorEventListener {
    public final static String STEP_BROADCAST_TAG = "com.koreatech.cse.termproject.step_scan";

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private long prevT, currT;
    private double[] rmsArray;
    private int rmsCount;
    private double steps;

    private final String LOGTAG = "StepMonitor";

    private static final int NUMBER_OF_SAMPLES = 5;
    private static final double AVG_RMS_THRESHOLD = 2.5;
    private static final double NUMBER_OF_STEPS_PER_SEC = 1.5;


    @Override
    public void onCreate() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);

        prevT = currT = 0;
        rmsCount = 0;
        rmsArray = new double[NUMBER_OF_SAMPLES];

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "step");
        wakeLock.acquire();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("aaAAA","aaAAA");
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
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            currT = event.timestamp;
            double dt = (currT - prevT) / 1000000;
            //Log.d(LOGTAG, "time difference=" + dt);
            prevT = currT;
            minute_count++;
            //Log.d(LOGTAG, minute_count+"");
            float[] values = event.values.clone();

            computeSteps(values);

        }
    }
    private static final int MINUTE_PER_MAXIMUN_STEP = 20; //80
    private static final int ONE_MINUTE_COUNT = 300; ///900
    private static final int STOP_COUNT_FIVE = 4;
    int stopCount = 0;
    private int minute_count=0;
    Intent intent = new Intent(StepMonitor.STEP_BROADCAST_TAG);
    private int totalStepCount = 0;
    private boolean isMoving =false;
    Date currentDate;
    private void computeSteps(float[] values) {
        double avgRms = 0;

        if(minute_count>ONE_MINUTE_COUNT) {
            Log.d("a", "" + stopCount + " " + isMoving);
            if(stopCount>STOP_COUNT_FIVE)
            {
                if(steps < MINUTE_PER_MAXIMUN_STEP&& steps>3)
                {
                    stopCount = 0;
                    intent.putExtra("isMoving", false);
                    intent.putExtra("steps", (int) totalStepCount);
                    sendBroadcast(intent);

                }
            }
            if (steps > MINUTE_PER_MAXIMUN_STEP && isMoving==false ) {
                currentDate = new Date();
                totalStepCount += steps;
                isMoving = true;
                stopCount = 0;
            }
            else if(totalStepCount > MINUTE_PER_MAXIMUN_STEP && isMoving==true)
            {
                if(steps < MINUTE_PER_MAXIMUN_STEP)
                {
                    Date nowDate = new Date();
                    intent.putExtra("currentDate", currentDate.getTime());
                    intent.putExtra("nowDate", nowDate.getTime());
                    intent.putExtra("isMoving", true);
                    intent.putExtra("steps", (int) totalStepCount);
                    totalStepCount = 0;
                    sendBroadcast(intent);
                    isMoving = false;
                }
            }
            else if(steps < MINUTE_PER_MAXIMUN_STEP && isMoving==false)
            {

                totalStepCount = 0;
                stopCount++;
            }


            steps =0;
            minute_count = 0;
        }
        // X, Y, Z
        double rms = Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);

        //MainActivity.rmsText.append(rms + "\n");
        // Log.d(">>> ", values[0] + ", " + values[1] + ", " + values[2] + ", " + rms);

        if(rmsCount < NUMBER_OF_SAMPLES) {
            // sampling
            rmsArray[rmsCount] = rms;
            rmsCount++;
        } else {

            double sum = 0;
            for(double e : rmsArray) {
                sum += e;
            }

            avgRms = sum / NUMBER_OF_SAMPLES;
            //Log.d(LOGTAG, "1sec avg rms: " + avgRms);


            // step result
            if(avgRms > AVG_RMS_THRESHOLD) {
                steps += NUMBER_OF_STEPS_PER_SEC;

                Log.d(LOGTAG, "steps: " + steps);

                //Intent intent = new Intent(STEP_BROADCAST_TAG);

                //sendBroadcast(intent);
            }

            // clear
            rmsCount = 0;
            for(int i = 0; i < NUMBER_OF_SAMPLES; i++) {
                rmsArray[i] = 0;
            }
            rmsArray[0] = rms;
            rmsCount++;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

