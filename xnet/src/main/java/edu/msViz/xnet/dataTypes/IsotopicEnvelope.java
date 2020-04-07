/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * Isotopic envelope containing a group of isotope traces and summary values
 * @author kyle
 */
public class IsotopicEnvelope {

    /**
     * unique envelope identifier
     */
    public final int envelopeID;

    /**
     * traces assigned to the envelope during trace clustering
     */
    public final IsotopeTrace[] traces;

    /**
     * IDs of the traces assigned to this envelope
     */
    public final Integer[] traceIDs;

    /**
     * charge state of the envelope
     */
    public final int chargeState;

    /**
     * sum of each trace's intensity sum
     */
    public double intensitySum = 0;

    /**
     * relative intensities of each trace
     */
    public double[] relativeIntensities;

    /**
     * left most trace's mz
     */
    public double monoisotopicMZ;

    /**
     * left most trace's rt
     */
    public double monoisotopicRT;

    /**
     * Constructor accepting a set of isotopic traces to include in the envelope
     * @param connSet set of traces belonging to the envelope
     * @param myEnvelopeID my assigned envelopeID
     */
    public IsotopicEnvelope(Set<IsotopeTrace> connSet, int myEnvelopeID)
    {
        // collected traces, sort by mz
        this.traces = connSet.toArray(new IsotopeTrace[0]);
        Arrays.sort(this.traces, Comparator.comparing(t -> t.centroidMZ));

        // collect envelope ID
        this.envelopeID = myEnvelopeID;

        this.relativeIntensities = new double[connSet.size()];
        this.traceIDs = new Integer[connSet.size()];
        this.monoisotopicMZ = Double.MAX_VALUE;
        this.monoisotopicRT = Double.MAX_VALUE;
        // iterate traces, collecting mz separations, intensity sum, monisotopic (min) mz, intensities
        double separationSum = 0;
        IsotopeTrace prevTrace = null;
        int i = 0;
        for(IsotopeTrace trace : this.traces)
        {
            this.traceIDs[i] = new Integer(trace.traceID);

            this.relativeIntensities[i++] = trace.intensitySum;

            this.monoisotopicMZ = this.monoisotopicMZ > trace.centroidMZ ? trace.centroidMZ : this.monoisotopicMZ;

            double rt = (trace.minRT + trace.maxRT)/2;
            this.monoisotopicRT = this.monoisotopicRT > rt ? rt : this.monoisotopicRT;

            this.intensitySum += trace.intensitySum;
            if(prevTrace != null)
                separationSum += (trace.centroidMZ - prevTrace.centroidMZ);
            prevTrace = trace;
        }

        // convert absolute intensities to relative intensities
        for(i = 0; i < this.relativeIntensities.length; i++)
            this.relativeIntensities[i] = this.relativeIntensities[i] / this.intensitySum;

        double averageSeparation = separationSum / (connSet.size() - 1);
        this.chargeState = (int)Math.round(1.0 / averageSeparation);
    }

}
