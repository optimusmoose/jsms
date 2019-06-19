package edu.umt.ms.traceSeg.metrics;

import edu.umt.ms.traceSeg.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks points and traces for scoring purposes.
 * Provides accessor methods to retrieve point and trace data after setting up a database with load() or addPoint()
 */
public class PointTraceDatabase {
    private Map<Integer, Point> points;
    private Map<Integer, Trace> traces;

    public PointTraceDatabase() {
        points = new HashMap<>();
        traces = new HashMap<>();
    }

    public Map<Integer, Point> getPoints() {
        return points;
    }

    public Point getPoint(int pointId) {
        return points.get(pointId);
    }

    public double getTraceCentroid(int traceId) {
        return traces.get(traceId).getCentroid();
    }

    public List<Point> getTracePoints(int traceId) {
        Trace t = traces.get(traceId);
        if (t != null) {
            return t.points;
        } else {
            return new ArrayList<>();
        }
    }
    
    public List<Integer> getTracePointIds(int traceId) {
        return getTracePoints(traceId).stream().map(p -> p.id).collect(Collectors.toList());
    }

    public List<Integer> getTraceIds() {
        return new ArrayList<>(traces.keySet());
    }

    public List<Integer> getNonNoiseTraceIds() {
        return traces.keySet().stream().filter(t -> t != 0).collect(Collectors.toList());
    }

    private void addPoint(Point pt) {
        points.put(pt.id, pt);
        int tid = pt.traceId;

        // initialize trace if necessary
        if (!traces.containsKey(tid)) {
            traces.put(tid, new Trace());
        }

        // add point to trace
        traces.get(tid).addPoint(pt);
    }

    public void addPoints(List<Point> points) {
        points.forEach(this::addPoint);
    }

    /**
     * Load a csv file into the PointTraceDatabase. Data is appended, not replaced.
     * @param filename CSV file containing point data
     * @throws FileNotFoundException
     */
    public void load(String filename) throws FileNotFoundException {
        int ptId = 1;
        try (Scanner fileScanner = new Scanner(new File(filename))) {
            // skip first line (assume header)
            fileScanner.nextLine();
            
            while (fileScanner.hasNextLine()) {
                // read each line split by comma
                String line = fileScanner.nextLine();
                String[] parts = line.split(",");

                // read fields from file as a point structure
                double ptMz = Double.parseDouble(parts[0]);
                float ptRT = Float.parseFloat(parts[1]);
                double ptInt = Double.parseDouble(parts[2]);
                int ptTrace = Integer.parseInt(parts[3]);

                // add point to the database
                Point p = new Point(ptId, ptMz, ptRT, ptInt);
                p.traceId = ptTrace;
                this.addPoint(p);
                ptId++;
            }
        }
    }

    public void save(String filename) throws FileNotFoundException {
        // simply write each point to the specified file
        try (PrintStream fileWriter = new PrintStream(filename)) {
            points.values().forEach(pt -> fileWriter.format("%f,%f,%f,%d", pt.mz, pt.rt, pt.intensity, pt.traceId));
        }
    }
}

class Trace {
    public double centroidWeightSum;
    public double intensitySum;
    public List<Point> points = new ArrayList<>();
    
    private double centroid = 0;
    public double getCentroid() {
        if (centroid == 0) {
            centroid = centroidWeightSum / intensitySum;
        }
        return centroid;
    }
    
    public void addPoint(Point pt) {
        points.add(pt);
        centroidWeightSum += pt.mz * pt.intensity;
        intensitySum += pt.intensity;
    }
}