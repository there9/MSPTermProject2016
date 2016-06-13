package com.koreatech.cse.termproject;

public class LocationInfo {
    String locationName = "없음";
    long time = -5;
    static int totalStepCount =0;
    static long totalMovingTime =0;

    LocationInfo() {

    }

    public float getTime() {
        return time;
    }

    public void timeCompare(long time, String locationName)
    {
        if(this.time<time && locationName != "실내" && locationName != "실외")
        {
            this.time = time;
            this.locationName = locationName;
        }
    }

    public String getLocationName() {
        return locationName;
    }

    public  static int totalSumStep(int step) {
        return totalStepCount += step;
    }

    public long totalSumMovingTime(long movingTime) {
        return totalMovingTime += movingTime;
    }
}
