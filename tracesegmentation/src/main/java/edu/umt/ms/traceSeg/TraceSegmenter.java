package edu.umt.ms.traceSeg;

import java.lang.Math;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import java.text.ParseException;

public class TraceSegmenter {

    private PointDatabaseConnection connection;

    private double RT_WIDTH = 0;
    private double MZ_WIDTH = 0;
    private double minIntensity = 0;
    private double totalInt = 0;
    private double keepInt = 0;

    int gridx = 10;
    int gridy = 100000;
    double[][] average_array = new double[gridx][gridy];
    double[][] stdDev_array = new double[gridx][gridy];
    int[][] counter_array = new int[gridx][gridy];

    double maxMz = 0;
    double maxRt = 0;
    double minMz = 10000;
    double minRt = 10000;
    int numStdDev = 2;

    public TraceSegmenter(PointDatabaseConnection connection, TraceParametersProvider parametersProvider) {
        this.connection = connection;
    }

    public void run() throws Exception {

      connection.deleteTraces();

      //TO get all points in file, pass in 0s. Otherwise pass in window.
      //List<Point> preFilter = connection.getAllPoints(minMzTemp, maxMzTemp, minRtTemp, maxRtTemp, 0);
      List<Point> preFilter = connection.getAllPoints(0, 0, 0, 0, 0);


      for(int x = 0; x < gridx; ++x){
        for(int y = 0; y < gridy; ++y){
          counter_array[x][y] = 1;
        }
      }


      for(int i = 0; i < preFilter.size(); i++){
        if(preFilter.get(i).mz > maxMz) maxMz = preFilter.get(i).mz;
        if(preFilter.get(i).mz < minMz) minMz = preFilter.get(i).mz;
        if(preFilter.get(i).rt > maxRt) maxRt = preFilter.get(i).rt;
        if(preFilter.get(i).rt < minRt) minRt = preFilter.get(i).rt;
      }


      System.out.println("Creating Average Grid.");

      for(int i = 0; i < preFilter.size(); i++){
        int x = (int)(Math.floor((preFilter.get(i).mz-minMz)/(maxMz-minMz)*(gridx-1)));
        int y = (int)(Math.floor((preFilter.get(i).rt-minRt)/(maxRt-minRt)*(gridy-1)));
        average_array[x][y]+=preFilter.get(i).intensity;
        counter_array[x][y]+= 1;

      }

      for(int x = 0; x < gridx; ++x){
        for(int y = 0; y < gridy; ++y){
          average_array[x][y] = average_array[x][y]/counter_array[x][y];
        }
      }

      System.out.println("Creating stdDev Grid.");

      for(int i = 0; i < preFilter.size(); i++){
        int x = (int)(Math.floor((preFilter.get(i).mz-minMz)/(maxMz-minMz)*(gridx-1)));
        int y = (int)(Math.floor((preFilter.get(i).rt-minRt)/(maxRt-minRt)*(gridy-1)));
        stdDev_array[x][y] += Math.pow((preFilter.get(i).intensity - average_array[x][y]),2);

      }

      for(int x = 0; x < gridx; ++x){
        for(int y = 0; y < gridy; ++y){
          if(counter_array[x][y]>1){
            stdDev_array[x][y] = Math.sqrt((1.0/(counter_array[x][y]-1.0)) * stdDev_array[x][y]);
          }
        }
      }



      //Get ordered Treemap for scans.
      TreeMap<Float, ArrayList<Double>> scans = getScans(preFilter);

      //Get Total Intensity from File.
      calculateIntensity(preFilter);

      //Get Keys from scans
      Set<Float> rtKeys = scans.keySet();

      //Calculate RT Width
      calculateRtWidth(rtKeys);

      //Calculate MZ Width
      calculateMzWidth(rtKeys,scans,preFilter);

      //Sort preFilter to go intensity first.
      System.out.println("Beginning Sort");
      preFilter.sort(Comparator.comparing(Point::getIntensity));
      System.out.println("Finish Sort");


      //Instantiate Arraylists for intensity ordered points, and mz ordered points.
      List<Point> points = new ArrayList<Point>();

      //Filter Points
      points = filterPoints(preFilter);

      //Clear filtered points to hopefully save space.
      preFilter = null;
      System.gc();

      //Create Graph

      Graph graph = new Graph(points.size());

      //Fill Graph with filtered points
      for(int i = 0; i < points.size();i++){
        graph.addPt(points.get(i));
      }

      //link points together
      linkPoints(connection,points,graph);
      
      points = null;
      System.gc();

      //Get hash of traces from graph
      Hashtable<Integer, ArrayList<Point>> hashTraces = graph.findComponents();

      //Write points to database.
      writePoints(hashTraces);

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

    public void calculateIntensity(List<Point> preFilter){
      for(int i = 0; i < preFilter.size(); ++i){
        if(i%100000 == 0){
          System.out.println("Summing Intensity: " + (double)i/preFilter.size()*100);
        }
        //totalInt += Math.log(preFilter.get(i).intensity);
        totalInt += preFilter.get(i).intensity;
      }
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

      for(Float key: rtKeys){
        ArrayList<Double> scanPoints = scans.get(key);
        Double minMz = 100.0;
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

        sumMz += minMz;
      }
      double a = centroidCounter;
      double b = preFilter.size();
      if((a/b) < .30){
        MZ_WIDTH = (sumMz/rtKeys.size());
        RT_WIDTH = RT_WIDTH * 4;
        //totalInt = (totalInt/(preFilter.size()/2));
        totalInt = (totalInt/(preFilter.size()));
        numStdDev = 1;
        System.out.println("Detected that File is Centroid");
      }else{
        MZ_WIDTH = (sumMz*10/rtKeys.size());

        totalInt = (totalInt/(preFilter.size()/2));
        numStdDev = 3;
        System.out.println("Detected that File is Profile");
      }
      System.out.println("The average MZ seperation is " + MZ_WIDTH);

    }

    public ArrayList<Point> filterPoints(List<Point> preFilter){

      ArrayList<Point> points = new ArrayList<Point>();

      for(int i = preFilter.size()-1; i >=0; i--){
        if(i%100000 == 0){
          System.out.println("Filtering Points: " + (double)i/preFilter.size()*100);
        }
        //keepInt += Math.log(preFilter.get(i).intensity);
        keepInt += preFilter.get(i).intensity;
        points.add(preFilter.get(i));
        //if(keepInt >= totalInt) break;
        //if(preFilter.get(i).intensity <= totalInt) break;
        if(i <= (preFilter.size()*.50)) break;
      }

      //Get Min Intensity
      minIntensity = points.get(points.size() -1).intensity;

      System.out.println("Size before filter: " + preFilter.size() + ". Size after filter: " + points.size());

      return points;
    }

    public double getDistance(Point p1, Point p2) {
      //return Math.sqrt(Math.pow((p1.rt - p2.rt),2)/RT_WIDTH + Math.pow((p1.mz - p2.mz),2)/MZ_WIDTH);
      return Math.sqrt(Math.pow((p1.rt - p2.rt),2)/RT_WIDTH + Math.pow((p1.mz - p2.mz),2)/MZ_WIDTH);
      //return Math.sqrt(Math.pow((p1.mz - p2.mz),2)/MZ_WIDTH);


    }

    public void linkPoints(PointDatabaseConnection connection, List<Point> intPoints,  Graph graph)throws Exception {

        for(int i = 0; i < intPoints.size(); i++){
          if(i % 100000 == 0){
            //System.out.print("\033[H\033[2J");
            System.out.println("Percentage Linked: " + ((double)i/intPoints.size())*100);
          }



          int x = (int)(Math.floor((intPoints.get(i).mz-minMz)/(maxMz-minMz)*(gridx-1)));
          int y = (int)(Math.floor((intPoints.get(i).rt-minRt)/(maxRt-minRt)*(gridy-1)));
          double avgMean = 0;
          double avgStd = 0;


          //System.out.println(average_array[x][y] + " : " + counter_array[x][y] + " : " + stdDev_array[x][y]);
          if(intPoints.get(i).intensity > (average_array[x][y] + (numStdDev * stdDev_array[x][y]))) {

            double minMzWin = intPoints.get(i).mz - MZ_WIDTH;
            double maxMzWin = intPoints.get(i).mz + MZ_WIDTH;
            float minRtWin = (float)(intPoints.get(i).rt - RT_WIDTH*10);
            float maxRtWin = (float)(intPoints.get(i).rt + RT_WIDTH*10);

            List<Point> windowPts = connection.getAllPoints(minMzWin, maxMzWin, minRtWin, maxRtWin, minIntensity);

            for(int j = 0; j < windowPts.size(); j++){
              windowPts.get(j).distance = getDistance(intPoints.get(i),windowPts.get(j));
            }

            double intensity = intPoints.get(i).intensity;

            windowPts.sort(Comparator.comparing(Point::getDistance));

            for(int j = 0; j < windowPts.size(); j++){
              boolean conn = graph.isConnected(intPoints.get(i).id,windowPts.get(j).id);
              if(!(conn))
              {
                graph.union(intPoints.get(i).id,windowPts.get(j).id);
                if (intensity < 0){
                  break;
                }
                double confidence = 0.0;
                intensity = intensity - (Math.abs(intensity - windowPts.get(j).intensity) * (windowPts.get(j).distance/100000000));//windowPts.get(j).distance;
                confidence = (intensity-(intensity*windowPts.get(j).distance))/intPoints.get(i).intensity;
                windowPts.get(j).link.add(intPoints.get(i));
                windowPts.get(j).confidence.add(confidence);
              }
            }
          }
        }
    }

    public void writePoints(Hashtable<Integer, ArrayList<Point>> hashTraces) throws Exception {
      Set<Integer> keys = hashTraces.keySet();
      int nextTraceId = 0;
      for(Integer key: keys){
        if(hashTraces.get(key).size() > 5){
          for(int i = 0; i < hashTraces.get(key).size(); i++){
            connection.writePoint(nextTraceId, hashTraces.get(key).get(i));
          }
          ++nextTraceId;
        }
      }
      System.out.println("Number of traces = " + nextTraceId);
    }
}


class Graph {
    public int numV;
    public int numE;
    public Hashtable<Integer, Vertex> vertexList;
    public Hashtable<Integer, Point> pointList;
    public Graph(int size){
      this.vertexList = new Hashtable<Integer,Vertex>();
      this.pointList = new Hashtable<Integer, Point>();
      this.numV = size;
    }

    class Vertex {
        public int id;
        public ArrayList<Vertex> adjacencyList;
        public int group;

        public Vertex(int id){
          this.adjacencyList = new ArrayList<Vertex>();
          this.id = id;
          this.group = id;
        }
    }

    public void addPt(Point p){
      Vertex v = new Vertex(p.id);
      this.vertexList.put(p.id,v);
      this.pointList.put(p.id,p);
    }
    public void dfs(Vertex v){
      for (Vertex adj : v.adjacencyList) {
        if(adj.group != v.group){
          adj.group = v.group;
          dfs(adj);
        }
        else{
          return;
        }
      }
    }
    public void union(int a,int b){
      this.numE+=1;
      if(this.vertexList.get(a).group < this.vertexList.get(b).group){

        this.vertexList.get(b).group = this.vertexList.get(a).group;
        dfs(this.vertexList.get(b));
        dfs(this.vertexList.get(a));

      }
      else{

        this.vertexList.get(a).group = this.vertexList.get(b).group;
        dfs(this.vertexList.get(a));
        dfs(this.vertexList.get(b));


      }
      this.vertexList.get(a).adjacencyList.add(this.vertexList.get(b));
      this.vertexList.get(b).adjacencyList.add(this.vertexList.get(a));
    }
    public boolean isConnected(int a,int b){
      if(this.vertexList.get(a).group == this.vertexList.get(b).group){
        dfs(this.vertexList.get(a));
        dfs(this.vertexList.get(b));
        return true;
      }
      else {
        return false;
      }
    }
    public Hashtable<Integer, ArrayList<Point>> findComponents(){
      Hashtable<Integer, ArrayList<Point>>  hashTraces = new Hashtable<Integer, ArrayList<Point>>();

      Set<Integer> keys = this.vertexList.keySet();

      for(Integer key: keys){
        if(hashTraces.containsKey(this.vertexList.get(key).group)) {
          hashTraces.get(this.vertexList.get(key).group).add(this.pointList.get(key));
        }else{
          ArrayList<Point> trace = new ArrayList<Point>();
          trace.add(this.pointList.get(key));
          hashTraces.put(this.vertexList.get(key).group,trace);
        }
      }
      return hashTraces;
    }
}
