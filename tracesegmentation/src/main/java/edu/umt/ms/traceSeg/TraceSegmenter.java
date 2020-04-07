package edu.umt.ms.traceSeg;

import java.lang.Math;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.ParseException;

import java.util.HashMap;

public class TraceSegmenter {

    private PointDatabaseConnection connection;

    private double RT_WIDTH = 0;
    private double MZ_WIDTH = 0;

    private double BIG_STD_DEV = 0;
    private double SMALL_STD_DEV = 0;

    private int pointLimit = 0;

    int GRIDX = 1;
    int GRIDY = 1;

    int LEFT = 0;
    int RIGHT = 0;

    double averageInt = 0;
    double stddev = 0;

    HashMap<Integer, ArrayList<Point>> hashTraces = new HashMap<Integer, ArrayList<Point>>();
    HashMap<Integer, ArrayList<Point>> finalTraces = new HashMap<Integer, ArrayList<Point>>();

    List<Point> intPoints = new ArrayList<Point>();
    List<Point> mzPoints;
    List<Point> windowPts = new ArrayList<Point>();
    List<Point> outputPoints = new ArrayList<Point>();

    double maxMz = 0;
    double maxRt = 0;
    double minMz = Double.POSITIVE_INFINITY;;
    double minRt = Double.POSITIVE_INFINITY;;

    public TraceSegmenter(PointDatabaseConnection connection) {
        this.connection = connection;
    }

    public void run() throws Exception {

      connection.deleteTraces();

      //TO get all points in file, pass in 0s. Otherwise pass in window.
      List<Point> preFilter = connection.getAllPoints(0, 0, 0, 0, 0);

      calcStats(preFilter);

      System.out.println("Size before Filter: " + preFilter.size());
      intPoints = filter(preFilter);
      System.out.println("Size after Fitler: " + intPoints.size());
      mzPoints = new ArrayList<Point>(intPoints.size());

      for(int i = 0 ; i < intPoints.size(); ++i){
        mzPoints.add(intPoints.get(i));
      }

      //Clear filtered points to hopefully save space.
      preFilter = null;
      System.gc();
      //Get ordered Treemap for scans.

      TreeMap<Float, ArrayList<Double>> scans = getScans(intPoints);

      //Get Keys from scans
      Set<Float> rtKeys = scans.keySet();

      //Calculate RT Width
      calculateRtWidth(rtKeys);

      //Calculate MZ Width
      calculateMzWidth(rtKeys,scans,intPoints);

      //Sort preFilter to go intensity first.
      System.out.println("Beginning intensity Sort");
      intPoints.sort(Comparator.comparing(Point::getIntensity));
      System.out.println("Finish intensity Sort");

      System.out.println("Beginning mz Sort");
      mzPoints.sort(Comparator.comparing(Point::getMz));
      System.out.println("Finish mz Sort");

      for(int i = 0; i < mzPoints.size(); i++){
        mzPoints.get(i).order = i;
        mzPoints.get(i).group = i;
      }

      System.out.println("Beginning Linking");
      linkPoints();
      System.out.println("Finish Linking");

      System.out.println("Writing Points");
      writePoints();

    }

    public void calcStats(List<Point> preFilter){
      for(int i = 0; i < preFilter.size(); i++){
        averageInt += preFilter.get(i).intensity;
        if(preFilter.get(i).mz > maxMz) maxMz = preFilter.get(i).mz;
        if(preFilter.get(i).mz < minMz) minMz = preFilter.get(i).mz;
        if(preFilter.get(i).rt > maxRt) maxRt = preFilter.get(i).rt;
        if(preFilter.get(i).rt < minRt) minRt = preFilter.get(i).rt;
      }
      averageInt/=preFilter.size();

      for(int i = 0; i < preFilter.size(); i++){
        stddev += Math.pow(preFilter.get(i).intensity - averageInt,2);
      }
      stddev = Math.sqrt(stddev/(preFilter.size()));
    }

    public List<Point> filter(List<Point> preFilter){
      List<Point> intPoints = new ArrayList<Point>();

      for(int i = 0; i < preFilter.size(); i++){
        if(preFilter.get(i).intensity > (averageInt + (SMALL_STD_DEV*stddev))){
          intPoints.add(preFilter.get(i));
        }
      }
      return intPoints;
    }

    public TreeMap<Float, ArrayList<Double>> getScans(List<Point> preFilter){
      TreeMap<Float, ArrayList<Double>> scans = new TreeMap<Float, ArrayList<Double>>();
      for(int i = 0; i < preFilter.size(); ++i){
        if(i%100000 == 0){
          //System.out.print("\033[H\033[2J");
          System.out.println("Creating Scans: " + (double)i/preFilter.size()*100);
        }
        ArrayList<Double> scanPoints = scans.get(preFilter.get(i).rt);
        if(scanPoints == null){
          scanPoints = new ArrayList<Double>();
          scans.put(preFilter.get(i).rt, scanPoints);
        }else{
          scanPoints.add(preFilter.get(i).mz);
        }
      }
      return scans;
    }

    public void calculateRtWidth(Set<Float> rtKeys ){
      float sum = 0;
      Float prev = null;
      for(Float key: rtKeys){
        if(prev == null){
          prev = key;
        }else{
          sum += (key-prev);
          prev = key;
        }
      }
      RT_WIDTH =  sum/rtKeys.size();
    }

    public void calculateMzWidth(Set<Float> rtKeys,TreeMap<Float, ArrayList<Double>> scans,List<Point> preFilter){

      Double prevMz = 0.0;
      float sumMz = 0;
      int centroidCounter = 0;
      int sumCounter = 0;
      for(Float key: rtKeys){
        ArrayList<Double> scanPoints = scans.get(key);
        Double minMz = Double.POSITIVE_INFINITY;
        Double curMz = 0.0;
        Double prevDif = 0.0;
        Collections.sort(scanPoints);
        for(int i = 0; i < scanPoints.size(); ++i){
          if(i == 0){
            prevMz = scanPoints.get(i);

          }else{
            curMz = (scanPoints.get(i) - prevMz);

            BigDecimal bd = new BigDecimal(curMz);
            bd = bd.round(new MathContext(3));
            double rounded = bd.doubleValue();

            if(rounded == prevDif){
              ++centroidCounter;
            }

            prevDif = rounded;
            if(curMz < minMz){
              minMz = curMz;
            }
            prevMz = scanPoints.get(i);
          }
        }
        if(scanPoints.size() > 1){
          sumMz += minMz;
          ++sumCounter;
        }
      }
      double a = centroidCounter;
      double b = preFilter.size();
      if((a/b) < .30){
        MZ_WIDTH = (sumMz/sumCounter);
        pointLimit = 3;
        System.out.println("Detected that File is Centroid");
      }else{
        MZ_WIDTH = 10*(sumMz/sumCounter);
        pointLimit = 10;
        System.out.println("Detected that File is Profile");
      }
      if (MZ_WIDTH > .01) MZ_WIDTH = .01;
      System.out.println("BIG_STD_DEV: " + BIG_STD_DEV + " SMALL_STD_DEV: " + SMALL_STD_DEV);
      System.out.println("The average MZ seperation is " + MZ_WIDTH);
      System.out.println("The average RT seperation is " + RT_WIDTH);
    }

    public void walk(int id,double mz){
      LEFT = id;
      RIGHT = id;

      windowPts.clear();
      while(LEFT > 10 && mzPoints.get(LEFT).mz > mz - 2*MZ_WIDTH){
        LEFT-=10;
      }
      while(RIGHT < mzPoints.size()-11 && mzPoints.get(RIGHT).mz < mz + 2*MZ_WIDTH){
        RIGHT+=10;
      }

      for(int i = LEFT; i <= RIGHT; ++i){
        windowPts.add(mzPoints.get(i));
      }
    }

    public int findRoot(Point p){
      while (p.order != p.group){
        p = mzPoints.get(p.group);
      }
      return p.order;
    }

    public void pointUnion(Point p1, Point p2){
      if(p1.group < p2.group){
        p2.group = p1.group;
      }else{
        p1.group = p2.group;
      }
    }

    public void linkPoints()throws Exception {
      for(int i = intPoints.size()-1; i >= 0; i--){
        if(i % 100000 == 0){
          //System.out.print("\033[H\033[2J");
          System.out.println("Percentage Linked: " + ((double)i/intPoints.size())*100);
        }
        if(intPoints.get(i).intensity > (2*averageInt + (BIG_STD_DEV*stddev))) {

          walk(intPoints.get(i).order, intPoints.get(i).mz);
          for(int j = windowPts.size()-1; j >=0; j--){
            if(Math.abs(windowPts.get(j).rt-intPoints.get(i).rt) < 10 * RT_WIDTH && windowPts.get(j).intensity * .8 < intPoints.get(i).intensity) {
              windowPts.get(j).group = intPoints.get(i).group;
            }
          }
        }
      }
    }

    public void writePoints() throws Exception {
      HashMap<Integer, ArrayList<Point>> traceMap = new HashMap<Integer, ArrayList<Point>>();

      for(int j =0; j< intPoints.size(); ++j){
        if(!traceMap.containsKey(intPoints.get(j).group)) {
          traceMap.put(intPoints.get(j).group, new ArrayList<Point>());
          traceMap.get(intPoints.get(j).group).add(intPoints.get(j));
        }else{
          traceMap.get(intPoints.get(j).group).add(intPoints.get(j));
        }
      }

      Set<Integer> keys = traceMap.keySet();
      int nextTraceId = 0;
      for(Integer key: keys){
        if(traceMap.get(key).size() > 5){
          for(int i = 0; i < traceMap.get(key).size(); i++){
            connection.writePoint(nextTraceId, traceMap.get(key).get(i));
          }
          ++nextTraceId;
        }
      }
    }
}
