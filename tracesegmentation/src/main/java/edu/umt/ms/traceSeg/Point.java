package edu.umt.ms.traceSeg;

import com.google.gson.JsonArray;

/**
 * Simple point data format relevant to segmentation
 */
public class Point {
    public int id;
    public double mz;
    public float rt;
    public double intensity;
    public int traceId;

    public Point(int id, double mz, float rt, double intensity) {
        this.id = id;
        this.mz = mz;
        this.rt = rt;
        this.intensity = intensity;
    }

    public Point(JsonArray pointData) {
        id = pointData.get(0).getAsInt();
        mz = pointData.get(2).getAsDouble();
        rt = pointData.get(3).getAsFloat();
        intensity = pointData.get(4).getAsDouble();
    }
}
