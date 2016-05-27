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

            float[] values = event.values.clone();

            computeSteps(values);
        }
    }

    private void computeSteps(float[] values) {
        double avgRms = 0;

        // X, Y, Z 축 RMS값
        double rms = Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);

        //MainActivity.rmsText.append(rms + "\n");
        //Log.d(">>> ", values[0] + ", " + values[1] + ", " + values[2] + ", " + rms);

        if(rmsCount < NUMBER_OF_SAMPLES) {
            // sampling
            rmsArray[rmsCount] = rms;
            rmsCount++;
        } else {
            // calculate
            double sum = 0;
            for(double e : rmsArray) {
                sum += e;
            }

            avgRms = sum / NUMBER_OF_SAMPLES;
            Log.d(LOGTAG, "1sec avg rms: " + avgRms);

            // step result
            if(avgRms > AVG_RMS_THRESHOLD) {
                steps += NUMBER_OF_STEPS_PER_SEC;
                Log.d(LOGTAG, "steps: " + steps);

                Intent intent = new Intent(STEP_BROADCAST_TAG);
                intent.putExtra("steps", (int)steps);
                sendBroadcast(intent);
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