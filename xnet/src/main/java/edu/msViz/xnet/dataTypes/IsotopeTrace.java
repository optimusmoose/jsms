/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import edu.msViz.xnet.Utility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.koloboke.collect.map.FloatDoubleMap;
import com.koloboke.collect.map.hash.HashFloatDoubleMaps;

/**
 * Isotope trace class containing attributes required for trace clustering
 * @author kyle
 */
public class IsotopeTrace {

    /**
     * Filler value for gaps in aligned trace arcs
     */
    public static double ARC_FILL_VALUE = Double.MIN_VALUE;

    /**
     * trace's unique identifier
     */
    public final int traceID;

    /**
     * intensity weighted MZ average
     */
    public Double centroidMZ;

    /**
     * minimum RT value in trace
     */
    public Float minRT;

    /**
     * maximum RT value in trace
     */
    public Float maxRT;

    /**
     * intensity sum over all points in trace
     */
    public Double intensitySum;

    /**
     * map of RT value to intensity of point nearest centroidMZ
     */
    public Map<Float,Double> arc;

    /**
     * ID of envelope. Assigned post clustering in practice.
     * Assigned from mzTree file if training.
     */
    public Integer envelopeID;

    public List<Double> mzValues;

    public List<Float> rtValues;

    public List<Double> intensityValues;
    /**
     * Constructs an IsotopeTrace with the given traceID from the data arrays.
     * Arrays contain 3D MS point data, with each index corresponding to a single point
     * across all arrays
     * @param traceID trace's given ID
     * @param mzValues mz values of each data point in the array
     * @param rtValues rt values of each data point in the array
     * @param intensityValues intensity values of each data point in the array
     */
    public IsotopeTrace(int traceID, List<Double> mzValues, List<Float> rtValues, List<Double> intensityValues)
    {
        // ensure that the arrays passed are of the same length
        if(mzValues.size() != rtValues.size() || mzValues.size() != intensityValues.size())
            throw new IllegalArgumentException("Cannot construct IsotopeTrace: m/z, RT and intensity arrays must be the same length");

        this.traceID = traceID;
        this.mzValues = mzValues;
        this.rtValues = rtValues;
        this.intensityValues = intensityValues;
        // container for points arranged in scans
        Map<Float,ScanData> scans = new HashMap<>();

        // prepare for point iteration
        double centroidMzNumerator = 0;
        this.minRT = Float.MAX_VALUE;
        this.maxRT = Float.MIN_VALUE;
        this.intensitySum = 0.0;

        // iterate through each point
        for(int i = 0; i < mzValues.size(); i++)
        {
            // resolve point's values
            double mz = mzValues.get(i);
            float rt = rtValues.get(i);
            double intensity = intensityValues.get(i);

            // keep min/max RT values
            this.minRT = rt < this.minRT ? rt : this.minRT;
            this.maxRT = rt > this.maxRT ? rt : this.maxRT;

            // sort into scans
            if(scans.containsKey(rt))
                scans.get(rt).add(mz, intensity);
            else
                scans.put(rt, new ScanData(mz,intensity));

            // accumulate weighted mz average numerator and intensity sum
            centroidMzNumerator += mz * intensity;
            this.intensitySum += intensity;
        }

        // compute centroidMZ
        this.centroidMZ = centroidMzNumerator / this.intensitySum;

        // compute the trace's arc
        this.arc = this.computeArc(scans);
    }

    /**
     * Precompiled isotope trace constructor, accepting precomputed values
     * @param traceID given trace ID
     * @param centroidMZ given centroid m/z
     * @param minRT given minimum RT
     * @param maxRT given maximum RT
     * @param intensitySum given intensity sum
     */
    public IsotopeTrace(int traceID, Double centroidMZ, Float minRT, Float maxRT, Double intensitySum)
    {
        this.traceID = traceID;
        this.centroidMZ = centroidMZ;
        this.minRT = minRT;
        this.maxRT = maxRT;
        this.intensitySum = intensitySum;
    }

    /**
     * computes the arc for the trace
     * arc: map of RT value to intensity value for each scan's point
     * nearest to the centroid MZ value
     * @param scans each scan's data for a trace
     * @return the intensity values nearest to centroid mz for each scan in a trace
     */
    private FloatDoubleMap computeArc(Map<Float,ScanData> scans)
    {
        FloatDoubleMap arc = HashFloatDoubleMaps.newMutableMap();

        // iterate through each scan's data
        for(Float scanRT : scans.keySet())
        {
            ScanData scanData = scans.get(scanRT);

            // index of mz value nearest the centroid mz
            int nearestCentroidIndex = Utility.getNearestIndex(scanData.mzValues, this.centroidMZ);

            // collect the scan's intensity value nearest the centroid mz
            arc.put((float)scanRT, (double)scanData.intensityValues.get(nearestCentroidIndex));
        }
        return arc;
    }

    /**
     * Returns a full outer join on RT for each trace's rt,intensity arc
     * @param otherArc trace arc with which to align this trace's arc
     * @return Aligned trace arcs in 2D array (double[2][n]) format
     */
    public double[][] alignArcs(Map<Float,Double> otherArc)
    {
        double[][] alignedArcs;

        // union of this trace's rt values and other trace's rt values
        Set<Float> unifiedRtValues = new TreeSet<>();
        unifiedRtValues.addAll(this.arc.keySet());
        unifiedRtValues.addAll(otherArc.keySet());
        Iterator<Float> rtValIterator = unifiedRtValues.iterator();

        // now that we know the size of the set of rt values, instantiate
        // the 2 alignment arrays
        alignedArcs = new double[2][unifiedRtValues.size()];

        // iterate through each rt value, collecting for each trace
        // the trace's corresponding intensity if exists, else 0
        int i = 0;
        while(rtValIterator.hasNext())
        {
            Float rtValue = rtValIterator.next();
            alignedArcs[0][i] = this.arc.containsKey(rtValue) ? this.arc.get(rtValue) : ARC_FILL_VALUE;
            alignedArcs[1][i++] = otherArc.containsKey(rtValue) ? otherArc.get(rtValue) : ARC_FILL_VALUE;
        }

        return alignedArcs;
    }

    /**
     * Returns the maximum intensity value from the trace's arc
     * @return maximum intensity from trace's arc
     */
    public double getMaxIntensity()
    {
        return this.arc.values().stream().max(Comparator.comparing(d -> d)).get();
    }

    /**
     * Returns the RT value of the maximum intensity entry from the trace's arc
     * @return RT value of maximum intensity entry in arc
     */
    public float getMaxIntensityRT()
    {
        return this.arc.entrySet().stream().max(Comparator.comparing(d -> d.getValue())).get().getKey();
    }

    public double fwhm(){
      double intCutoff = getMaxIntensity()/2.0;
      double maxMZ = 0;
      double minMZ = Double.POSITIVE_INFINITY;
      for(int i = 0; i < this.intensityValues.size(); ++i){
        if(this.intensityValues.get(i) > intCutoff){
          if(this.mzValues.get(i) < minMZ){
            minMZ = this.mzValues.get(i) ;
          }
          if(this.mzValues.get(i)  > maxMZ){
            maxMZ = this.mzValues.get(i) ;
          }
        }
      }
      return (maxMZ-minMZ);
    }
    /**
     * Structure for containing a string of data points corresponding
     * to a scan within a trace
     */
    private class ScanData
    {
        public ArrayList<Double> mzValues = new ArrayList<>();
        public ArrayList<Double> intensityValues = new ArrayList<>();

        public ScanData(double firstMz, double firstIntensity)
        {
            this.mzValues.add(firstMz);
            this.intensityValues.add(firstIntensity);
        }

        public void add(double mz, double intensity)
        {
            this.mzValues.add(mz);
            this.intensityValues.add(intensity);
        }
    }
}
