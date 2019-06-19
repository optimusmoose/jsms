/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import edu.msViz.xnet.dataTypes.TracesBundle;
import edu.msViz.xnet.probability.ProbabilityAggregator;
import java.util.List;

import java.io.FileWriter;
import java.io.IOException;


/**
 *
 * @author kyle,mathew
 */
public class TestRun {

    private TracesBundle tracesBundle;

    public static void main(String[] args) throws IOException
    {
        TestRun test = new TestRun();
        test.tracesBundle = Utility.loadValuesFromCSV(args[0]);
        List<IsotopeTrace> traces = test.tracesBundle.synthesize();
        FileWriter writer = new FileWriter("output.csv");

        try {
        	writer.write("mz,rt,intensity,traceID,envelopeID\n");
            List<IsotopicEnvelope> Envelopes = new TraceClusterer().clusterTraces(traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN, null);
            for(int i = 0; i < Envelopes.size(); i++) {
            	for (int j = 0; j < Envelopes.get(i).traces.length; j++){
            		for (int k = 0; k < Envelopes.get(i).traces[j].mzValues.size(); k++) {
            			String mz = Double.toString(Envelopes.get(i).traces[j].mzValues.get(k));
            			String rt = Float.toString(Envelopes.get(i).traces[j].rtValues.get(k));
            			String intensity = Double.toString(Envelopes.get(i).traces[j].intensityValues.get(k));
            			String traceID = Integer.toString(Envelopes.get(i).traces[j].traceID);
            			String envelopeID = Integer.toString(Envelopes.get(i).envelopeID);

            			writer.write(mz + "," + rt + "," + intensity + "," + traceID + "," + envelopeID+"\n");
            		}
            	}
            }
            writer.close();
        } catch (Exception ex) {
            System.err.println("Error || " + ex.getMessage());
            ex.printStackTrace();
        }

    }


}
