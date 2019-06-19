/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import edu.msViz.xnet.probability.ProbabilityAggregator;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.Assert;

/**
 *
 * @author kyle
 */
public class RealWorldTest {
    
    private List<IsotopeTrace> traces;
    private final List<Set<IsotopeTrace>> expectedEnvelopes;
    private String filePath;
    
    public RealWorldTest() 
    {
        expectedEnvelopes = new ArrayList<>();

    }
    
    @Test
    public void interleavedSlightOverlapTest()
    {
        filePath = "interleaved_slight_overlap.csv";
        System.out.println(filePath);
        
        traces = this.loadTracesResource(filePath);
        
        // expected set 1
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(4),
            traces.get(5),
            traces.get(7),
            traces.get(9)
        })));
        // expected set 2
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(6),
            traces.get(8),
            traces.get(0),
            traces.get(1),
            traces.get(2)  
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void singleSplitTest()
    {
        filePath = "single_split.csv";
        System.out.println(filePath);

        traces = this.loadTracesResource(filePath);        
        
        // expected set 1
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(0),
            traces.get(1),
            traces.get(2),
            traces.get(3),
            traces.get(4),
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void twoCollidingEnvsFourResultEnvs()
    {
        filePath = "2_colliding_envs_4_result_envs.csv";
        System.out.println(filePath);

        traces = this.loadTracesResource(filePath);
        
        // expected set 1
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(2),
            traces.get(4),
            traces.get(6),
            traces.get(7),
            traces.get(8),
        })));
        // expected set 2
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(5),
            traces.get(0),
            traces.get(1),  
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void twoEnvelopesInconsistentTest()
    {
        filePath = "two_nearby_inconsistent.csv";
        System.out.println(filePath);

        traces = this.loadTracesResource(filePath);
        
        
        // expected set 1
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(8),
            traces.get(0),
            traces.get(1),
            traces.get(9),
        })));
        // expected set 2
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(2),
            traces.get(5),
            traces.get(4),
            traces.get(6),  
        })));
        
        // traces accidentally captured during export
        traces.remove(3);
        traces.remove(6);
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void misalignedPeaksTest()
    {
        filePath = "misaligned_peaks.csv";
        System.out.println(filePath);
        traces = this.loadTracesResource(filePath);
        
        // expected set 1
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(5),
            traces.get(0),
            traces.get(1),
            traces.get(2),
        })));
        // expected set 2
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(4),
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void nonDescendingTest()
    {
        filePath = "non_descending.csv";
        System.out.println(filePath);
        traces = this.loadTracesResource(filePath);
        
        // expected set
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(0),
            traces.get(1),
            traces.get(2),
            traces.get(4),
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
        
    }
    
    @Test
    public void unassignedTraceTest()
    {
        filePath = "unassigned_trace.csv";
        System.out.println(filePath);
        traces = this.loadTracesResource(filePath);
        
        // expected set
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(0),
            traces.get(1),
            traces.get(2),
        })));
        
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    @Test
    public void fiveTraceFiveEnvelopesTest()
    {
        filePath = "5_traces_5_envelopes.csv";
        System.out.println(filePath);
        traces = this.loadTracesResource(filePath);
        
        // expected set
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(0),
            traces.get(1),
            traces.get(2),
            traces.get(8),
        })));
        // expected set 2
        expectedEnvelopes.add(new HashSet<>(Arrays.asList(new IsotopeTrace[] {
            traces.get(3),
            traces.get(4),
            traces.get(5),
            traces.get(6),
            traces.get(7),
        })));
       
        this.testExtractedEnvelopes(expectedEnvelopes, traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN);
    }
    
    // *********************HELPERS***********************
    
    private List<IsotopeTrace> loadTracesResource(String filename)
    {
        ClassLoader cl = getClass().getClassLoader();
        File file = new File(cl.getResource(filename).getFile());
        
        return Utility.loadValuesFromCSV(file).synthesize();
    }

    private void testExtractedEnvelopes(List<Set<IsotopeTrace>> expectedEnvelopes,  List<IsotopeTrace> inputTraces, ProbabilityAggregator.PROB_MODEL model)
    {
        List<IsotopicEnvelope> results = new TraceClusterer().clusterTraces(inputTraces, model, null);
        
        for(IsotopicEnvelope env : results)
        {   
            boolean isExpected = false;
            Set<IsotopeTrace> envTraces = new HashSet<>(Arrays.asList(env.traces));
            for(Set<IsotopeTrace> expectedEnvelope : expectedEnvelopes)
                if(expectedEnvelope.equals(envTraces))
                {
                    isExpected = true;
                    break;
                }  
            Assert.assertTrue(isExpected);
        }
    }
}
