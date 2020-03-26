package edu.umt.ms.traceSeg;

import com.google.gson.JsonArray;
import java.util.*;
/**
 * Simple point data format relevant to segmentation
 */
public class Point{
    public int index;
    public int id;
    public double mz;
    public float rt;
    public double intensity;
    public int traceId;
    public double distance;
    public int group;
    public ArrayList<Point> link;
    public ArrayList<Double> confidence;

    // public Point(int id, double mz, float rt, double intensity) {
    //
    //     this.id = id;
    //     this.mz = mz;
    //     this.rt = rt;
    //     this.intensity = intensity;
    // }
    public Point(int id, double mz, float rt, double intensity) {
        this.link = new ArrayList<Point>();
        this.confidence = new ArrayList<Double>();
        //this.index = index;
        this.id = id;
        this.group = id;
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

    public double getIntensity(){
      return intensity;
    }
    public double getDistance(){
      return distance;
    }
    public double getMz(){
      return mz;
    }
}