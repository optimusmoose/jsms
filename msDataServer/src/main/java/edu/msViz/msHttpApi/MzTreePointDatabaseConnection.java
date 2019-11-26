package edu.msViz.msHttpApi;

import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.IO.MsDataRange;
import edu.msViz.mzTree.MzTree;
import edu.msViz.mzTree.MsDataPoint;
import edu.umt.ms.traceSeg.Point;
import edu.umt.ms.traceSeg.PointDatabaseConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.lang.Math;


/**
 * Implements PointDatabaseConnection against an MzTree.
 * This provides direct data access for faster segmentation.
 */
public class MzTreePointDatabaseConnection implements PointDatabaseConnection {
    private MzTree mzTree;

    public MzTreePointDatabaseConnection(MzTree mzTree) {
        this.mzTree = mzTree;
    }

    @Override
    public List<Point> getAllPoints(double minMz, double maxMz, float minRt, float maxRt, double minIntensity) throws Exception {
      List<MsDataPoint> preFilter = new ArrayList<MsDataPoint>();
      List<Point> points = new ArrayList<Point>();

      preFilter = mzTree.query(minMz,maxMz,minRt,maxRt,0);

      if(minIntensity == 0){
        for(int i = 0; i < preFilter.size();i++){
          MsDataPoint msPt = preFilter.get(i);
          Point pt = new Point( msPt.pointID,msPt.mz,msPt.rt,msPt.intensity);
          points.add(pt);
        }
      }
      else{
        for(int i = 0; i < preFilter.size();i++){
          MsDataPoint msPt = preFilter.get(i);
          if(msPt.intensity > minIntensity){
            Point pt = new Point(msPt.pointID,msPt.mz,msPt.rt,msPt.intensity);
            points.add(pt);
          }
        }
      }
      return points;
    }


    @Override
    public void deleteTraces() throws Exception {
      mzTree.deleteTraces();
    }

    @Override
    public double getMinimumIntensity() throws Exception {
        return mzTree.head.intMin;
    }

    @Override
    public double getMaximumIntensity() throws Exception {
        return mzTree.head.intMax;
    }


    @Override
    public Point getHighestUnassignedPoint() throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        MsDataPoint point = mzTree.intensityTracker.getHighestUntraced();
        return point == null ? null : new Point(point.pointID, point.mz, point.rt, point.intensity);
    }

    @Override
    public int getNextTraceId() throws Exception {
        Optional<Integer> maxTrace = mzTree.traceMap.keySet().stream().max(Comparator.comparingInt(s -> s));
        return Math.max(maxTrace.orElse(0), 0) + 1;
    }

    @Override
    public void writePoint(int traceId, Point point) throws Exception {
        if (!mzTree.traceMap.containsKey(traceId)) {
            mzTree.insertTrace(traceId, 0);
        }
        mzTree.updateTraces(traceId, new Integer[] { point.id });
    }

    private Map<Integer, Double> calculatedMzResolutions;
    static final double BUCKET_FACTOR = 5;
    @Override
    public double getMzResolution(double nearMz) throws Exception {
        if (calculatedMzResolutions == null) {
            calculatedMzResolutions = new HashMap<Integer, Double>();
        }

        int bucket = ((int)(nearMz / BUCKET_FACTOR));
        if (!calculatedMzResolutions.containsKey(bucket)) {
            calculatedMzResolutions.put(bucket, calculateMzResolutionNear(bucket * BUCKET_FACTOR));
        }

        return calculatedMzResolutions.get(bucket);
    }

    /**
     * Brute-force finds the median difference between successive points in a same scan.
     * Calling this the "m/z resolution", but that may not actually be quite correct to say.
     * @param mz m/z value near which to find
     * @return the m/z resolution near the specified m/z value
     */
    private double calculateMzResolutionNear(double mz) throws Exception {
        List<MsDataPoint> points = mzTree.query(mz - 1, mz + 1, mzTree.head.rtMin, mzTree.head.rtMax, 0);
        points.sort(Comparator.comparingDouble((MsDataPoint p) -> p.rt).thenComparingDouble(p -> p.mz));

        List<Double> diffs = new ArrayList<Double>();
        for(int i = 1; i < points.size(); i++) {
            MsDataPoint current = points.get(i);
            MsDataPoint prev = points.get(i-1);
            if (current.rt == prev.rt) {
                double diff = current.mz - prev.mz;
                if (diff < 1) {
                    diffs.add(diff);
                }
            }
        }

        if (diffs.size() == 0) {
            throw new Exception("Could not detect nearby m/z resolution");
        }

        diffs.sort(Comparator.naturalOrder());

        // TODO: use outlier detection instead of assuming the median will work

        double median_diff = diffs.get(diffs.size()/2);
        return median_diff;
    }
}
