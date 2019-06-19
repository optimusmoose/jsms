package edu.umt.ms.traceSeg;

import java.util.ArrayList;
import java.util.List;

// TODO: Traces can only contain one point per scan (not for profile data -- maybe not at all depending on how bad centroiding is)
// TODO: Option for CSV database input/output
// TODO: Could remove p threshold and/or shrink m/z strip by figuring out mz variance as function of intensity using several hand-segmented windows from user.

public class TraceSegmenter {
    private boolean IS_CENTROID = false;
    private final double MAX_MZ_POINTS;
    // TODO: set the MAX_MZ_POINTS constants better
    private static final double MAX_MZ_POINTS_PROFILE = 5;
    private static final double MAX_MZ_POINTS_CENTROID = 0.5;
    
    private double PROBABILITY_THRESHOLD = 0.7;
    private double MAX_RT_DIST = 1.5;
    private double INT_FACTOR = 0.01;
    private double INT_LIMIT = 0.0001;
    private double RT_SCALE = 1;

    private PointDatabaseConnection connection;

    public TraceSegmenter(PointDatabaseConnection connection, TraceParametersProvider parametersProvider) {
        this.connection = connection;

        this.IS_CENTROID = parametersProvider.getCentroided().orElse(this.IS_CENTROID);
        this.PROBABILITY_THRESHOLD = parametersProvider.getProbabilityThreshold().orElse(this.PROBABILITY_THRESHOLD);
        this.MAX_RT_DIST = parametersProvider.getMaxRTDist().orElse(this.MAX_RT_DIST);
        this.INT_FACTOR = parametersProvider.getIntFactor().orElse(this.INT_FACTOR);
        this.INT_LIMIT = parametersProvider.getIntLimit().orElse(this.INT_LIMIT);
        this.RT_SCALE = parametersProvider.getRTScale().orElse(this.RT_SCALE);
        
        if (IS_CENTROID) {
            this.MAX_MZ_POINTS = TraceSegmenter.MAX_MZ_POINTS_CENTROID;
        } else {
            this.MAX_MZ_POINTS = TraceSegmenter.MAX_MZ_POINTS_PROFILE;
        }
    }

    private TraceList traces;

    /**
     * Performs trace segmentation.
     * @throws Exception
     */
    public void run() throws Exception {
        double intensity_min = connection.getMinimumIntensity();
        double intensity_max = connection.getMaximumIntensity();
        double intensity_range = intensity_max - intensity_min;

        traces = new TraceList();

        // TODO: determine a proper stop condition
        double minProcessIntensity = (intensity_min + this.INT_LIMIT * intensity_range);

        this.nextTraceId = connection.getNextTraceId();
        // assign traces until none are left
        Point highestUnassigned = connection.getHighestUnassignedPoint();
        while (highestUnassigned != null && highestUnassigned.intensity >= minProcessIntensity) {
            assignMostLikelyTrace(highestUnassigned);

            highestUnassigned = connection.getHighestUnassignedPoint();
        }

        // TODO: post-process: traces with single point should be considered noise
        // TODO: post-process or during-process to 'break' traces that are actually multiple traces
    }

    /**
     * Calculates probabilities of nearby traces, returning the closest and best traces
     * @param point The point to assign to a candidate trace
     * @return two integers: the closest trace and the best-match trace. If both are null, there were no traces.
     */
    private Integer[] getMostLikelyTraceHelper(Point point) {
        // find closest in m/z
        Integer absoluteClosestIndex = traces.getIndexOfClosestTrace(point.mz);
        Integer bestNearIndex = absoluteClosestIndex;

        if (absoluteClosestIndex != null) {

            // branch out within max m/z distance finding the best score

            double bestProb = getProbability(traces.get(absoluteClosestIndex), point);
            
            // check right
            int i = absoluteClosestIndex + 1;
            while (i < traces.size()) {
                if (!IS_CENTROID && getMzDist(traces.get(i), point) > MAX_MZ_POINTS) break;

                double prob = getProbability(traces.get(i), point);
                if (prob > bestProb) {
                    bestProb = prob;
                    bestNearIndex = i;
                }

                i++;
            }

            // check left
            i = absoluteClosestIndex - 1;
            while (i >= 0) {
                if (!IS_CENTROID && getMzDist(traces.get(i), point) > MAX_MZ_POINTS) break;

                double prob = getProbability(traces.get(i), point);
                if (prob > bestProb) {
                    bestProb = prob;
                    bestNearIndex = i;
                }

                i--;
            }
        }
        return new Integer[] { bestNearIndex, absoluteClosestIndex};
    }
    
    /**
     * Returns the distance from a point to a target trace in terms of number of m/z points,
     * as determined by the m/z resolution in the area
     * @param target
     * @param point
     * @return
     */
    private double getMzDist(Trace target, Point point) {
        double mzRes;
        try {
            mzRes = connection.getMzResolution(point.mz);
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
        
        // distance is positive point-to-edge value, or 0 if the point is "inside" the trace bounds,
        // scaled to the mz resolution
        double pmz = point.mz;
        if (pmz < target.getMinMz()) {
            return (target.getMinMz() - pmz) / mzRes;
        } else if (pmz > target.getMaxMz()) {
            return (pmz - target.getMaxMz()) / mzRes;
        } else {
            return 0;
        }
    }

    /**
     * Returns the distance from a point to a target trace in the RT dimension,
     * @param target The trace to check
     * @param point The point to check
     * @return the distance the point would have to be move to be considered "inside" the existing bounds:
     *         For points outside the target trace's bounds, the distance to the closer 'end' of the trace in RT dimension;
     *         For points already inside the target trace's bounds, 0.
     */
    private float getRtDist(Trace target, Point point) {
        // distance is positive point-to-edge value, or 0 if the point is "inside" the trace bounds
        float prt = point.rt;
        if (prt < target.getMinRt()) {
            return target.getMinRt() - prt;
        } else if (prt > target.getMaxRt()) {
            return prt - target.getMaxRt();
        } else {
            return 0f;
        }
    }

    /**
     * Calculates the probability that a point is in a specified target trace
     * @return The calculated probability
     */
    private double getProbability(Trace target, Point point) {
        if (point.intensity > target.getMinIntensity()) {
            return 0;
        }

        double mz_dist = getMzDist(target, point);
        float rt_dist = (float)(getRtDist(target, point) / this.RT_SCALE);
        double intNorm = (point.intensity / target.getMaxIntensity());
        if (mz_dist > MAX_MZ_POINTS || rt_dist > MAX_RT_DIST) {
            return 0;
        }
        
        double p = (1.0 - Math.pow(mz_dist/MAX_MZ_POINTS, 0.5) * (1-PROBABILITY_THRESHOLD)) *
                (1.0 - Math.pow(rt_dist/MAX_RT_DIST, 0.5) * (1-PROBABILITY_THRESHOLD)) *
                Math.pow(1 - Math.min(0.8, intNorm), INT_FACTOR);
        return p;
    }
    
    private int nextTraceId;

    /**
     * Assigns the specified point to the most likely trace it should belong to, as long as it passes the PROBABILITY_THRESHOLD
     * @param point Point to be assigned to some trace
     * @throws Exception
     */
    private void assignMostLikelyTrace(Point point) throws Exception {
        // grab closest trace in m/z within RT, and also the absolute closest
        Integer[] likelyTraces = getMostLikelyTraceHelper(point);
        Integer bestNearIndex = likelyTraces[0];
        Integer absoluteClosestIndex = likelyTraces[1];

        Trace bestNearTrace;

        if (bestNearIndex != null) {
            bestNearTrace = traces.get(bestNearIndex);
            if (getProbability(bestNearTrace, point) > PROBABILITY_THRESHOLD) {
                // add to existing trace, update point
                connection.writePoint(bestNearTrace.getId(), point);
                bestNearTrace.addPoint(point);

                // let traces list re-sort around this item
                traces.traceUpdated(bestNearIndex);
                return;
            }
        }

        // no match or not within probability allowance,
        // assign to next new trace id instead
        int traceId = nextTraceId;
        nextTraceId++;
        connection.writePoint(traceId, point);

        // begin tracking trace
        Trace t = new Trace(traceId);
        t.addPoint(point);

        // insert into the traces array at the correct index
        if (absoluteClosestIndex == null) {
            traces.insert(0, t);
        } else {
            double closestMz = traces.get(absoluteClosestIndex).getWeightedMz();
            if (closestMz < t.getWeightedMz()) {
                traces.insert(absoluteClosestIndex + 1, t);
            } else {
                traces.insert(absoluteClosestIndex, t);
            }
        }
    }
}

/**
 * Tracked information on segmented traces.
 */
class Trace {
    private int id;
    private double mzIntensity = 0.0;
    private double sumIntensity = 0.0;
    private double weightedMz = 0.0;
    private double minIntensity = Double.MAX_VALUE;
    private double maxIntensity = 0.0;
    private float minRt = Float.MAX_VALUE;
    private float maxRt = Float.MIN_VALUE;
    private double minMz = Double.MAX_VALUE;
    private double maxMz = Double.MIN_VALUE;

    public int getId() { return id; }
    public double getWeightedMz() { return weightedMz; }
    public double getMinIntensity() { return minIntensity; }
    public double getMaxIntensity() { return maxIntensity; }
    public float getMinRt() { return minRt; }
    public float getMaxRt() { return maxRt; }
    public double getMinMz() { return minMz; }
    public double getMaxMz() { return maxMz; }

    public Trace(int id) {
        this.id = id;
    }

    public void addPoint(Point pt) {
        mzIntensity += pt.intensity * pt.mz;
        sumIntensity += pt.intensity;
        weightedMz = mzIntensity / sumIntensity;
        if (pt.intensity < minIntensity) { minIntensity = pt.intensity; }
        if (pt.intensity > maxIntensity) { maxIntensity = pt.intensity; }
        if (pt.rt < minRt) { minRt = pt.rt; }
        if (pt.rt > maxRt) { maxRt = pt.rt; }
        if (pt.mz < minMz) { minMz = pt.mz; }
        if (pt.mz > maxMz) { maxMz = pt.mz; }
    }
}

/**
 * Wrapper for a list of traces that provides convenience methods for keeping the list
 * sorted for fast searching
 */
class TraceList {
    private List<Trace> list = new ArrayList<>();

    /**
     * Retrieves a trace by index in the list
     * @param index the index of the trace to retrieve
     * @return
     */
    public Trace get(int index) {
        if (index >= 0 && index < list.size()) {
            return list.get(index);
        } else {
            return null;
        }
    }

    public int size() {
        return list.size();
    }

    /**
     * Efficiently searches the list for the trace with its current weighted m/z matching closest to targetMz
     * @param targetMz the m/z value to search for
     * @return the current index within the list of the trace matching the criteria, or null if the list is empty
     */
    public Integer getIndexOfClosestTrace(double targetMz) {
        // binary search to find closest point in sorted traces list

        int lo = 0;
        int hi = list.size()-1;
        if (hi < 0) {
            return null;
        }

        double best_diff = Double.MAX_VALUE;
        Integer best_index = null;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            double lastMz = list.get(mid).getWeightedMz();
            double diff = Math.abs(lastMz - targetMz);

            if (diff < best_diff) {
                best_diff = diff;
                best_index = mid;
            }

            if (targetMz < lastMz) {
                hi = mid - 1;
            } else if (targetMz > lastMz) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }

        return best_index;
    }

    /**
     * Inserts the given trace before index in the list. index should be specified such that the list remains in sorted order by m/z
     */
    public void insert(int index, Trace trace) {
        list.add(index, trace);
    }

    /**
     * Swaps two elements within the list
     */
    private void swap(int idx1, int idx2) {
        Trace t1 = list.get(idx1);
        Trace t2 = list.get(idx2);

        list.set(idx1, t2);
        list.set(idx2, t1);
    }

    /**
     * Compares and swaps the trace at origIdx with the trace to either its left or right to maintain m/z sort
     * Continues to swap until no more swaps are necessary
     * @param index index of the trace that has been updated necessitating a swap
     * @param left true to check to the left, otherwise false.
     */
    private void comp_and_swap(int index, boolean left) {
        if (left) {
            // swap while index's is smaller than its left neighbor's m/z
            while (index > 0 && get(index).getWeightedMz() < get(index-1).getWeightedMz()) {
                swap(index, index - 1);
                index--;
            }
        } else {
            // swap while index's is greater than its right neighbor's m/z
            while (index+1 < size() && get(index).getWeightedMz() > get(index+1).getWeightedMz()) {
                swap(index, index+1);
                index++;
            }
        }
    }

    /**
     * To be called when a trace has been updated, so that its location in the list can be maintained.
     * @param index index of the trace to check
     */
    public void traceUpdated(int index) {
        this.comp_and_swap(index, true);
        this.comp_and_swap(index, false);
    }
}