package com.koreatech.cse.termproject;

/**
 * Created by HOME on 2016-05-27.
 */
public class LocatioInfo {
    String locationName;
    float time;
    int step;
    static int totalStepCount;
    static int totalMovingTime;
    LocatioInfo(String locationName, int step, float time)
    {
        this.locationName = locationName;
        this.step = step;
        this.time = time;
    }
    public float getTime() {
        return time;
    }

    public int getStep() {
        return step;
    }
    public String getLocationName() {
        return locationName;
    }
    public int totalSumStep(int step)
    {
        return totalStepCount += step;
    }
    public int totalSumMovingTime(int movingTime)
    {
        return totalMovingTime += movingTime;
    }
}
