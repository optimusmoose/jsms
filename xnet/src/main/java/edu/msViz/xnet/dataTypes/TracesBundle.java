/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundle of trace data points, organized by trace.
 * Arranges trace point data into groups corresponding to traceID.
 * Can be used to turn raw data into IsotopeTrace objects
 * @author kyle
 */
public class TracesBundle {
    public final Map<Integer,ArrayList<Double>> tracesMZ = new HashMap<>();
    public final Map<Integer,ArrayList<Float>> tracesRT = new HashMap<>();
    public final Map<Integer,ArrayList<Double>> tracesIntensity = new HashMap<>();
    public final Map<Integer,Integer> traceMap = new HashMap<>();
    
    /**
     * Adds new trace data from the given point to the bundle
     * @param traceID point's traceID
     * @param mz point's mz value
     * @param rt point's rt value
     * @param intensity point's intensity value
     * @param envelopeID trace's env ID (0 for no envelope)
     */
    public void addPoint(int traceID, double mz, float rt, double intensity, int envelopeID)
    {
        if(traceID == 0)
            throw new IllegalArgumentException("Points without a trace (traceID == 0) are not to be bundled");
        
        if(this.traceMap.containsKey(traceID))
        {
            this.tracesMZ.get(traceID).add(mz);
            this.tracesRT.get(traceID).add(rt);
            this.tracesIntensity.get(traceID).add(intensity);
        }
        else
        {
            this.traceMap.put(traceID,envelopeID);
            this.tracesMZ.put(traceID, new ArrayList<>(Arrays.asList(mz)));
            this.tracesRT.put(traceID, new ArrayList<>(Arrays.asList(rt)));
            this.tracesIntensity.put(traceID, new ArrayList<>(Arrays.asList(intensity)));
        }
    }
    
    /**
     * Converts the trace bundle into a list of IsotopeTrace objects
     * @return list of isotope trace objects synthesized from bundled trace data
     */
    public List<IsotopeTrace> synthesize()
    {
        List<IsotopeTrace> traces = new ArrayList<>();
        this.traceMap.keySet().stream().forEach((traceID) -> 
        {
            IsotopeTrace trace = new IsotopeTrace(traceID, this.tracesMZ.get(traceID), this.tracesRT.get(traceID), this.tracesIntensity.get(traceID));
            trace.envelopeID = traceMap.get(traceID);
            traces.add(trace);
        });
        return traces;
    }
}
