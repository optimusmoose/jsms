/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.Assert;

/**
 * Test the creation and (potential) facilities of the IsotopicEnvelope class
 * @author kyle
 */
public class IsotopicEnvelopeTest {
    
    private static final double DELTA = 1e-15;
    
    Set<IsotopeTrace> traces;
    
    public IsotopicEnvelopeTest() 
    { 
        traces = new HashSet<>(Arrays.asList(new IsotopeTrace[]{
            new IsotopeTrace(1, 1.02, 0f, 0f, 1.0),
            new IsotopeTrace(2, 1.512, 0f, 0f, 3.0),
            new IsotopeTrace(3, 1.98, 0f, 0f, 4.0),
            new IsotopeTrace(4, 2.49, 0f, 0f, 3.0),
            new IsotopeTrace(5, 3.04, 0f, 0f, 1.0),
        }));
    }

    @Test
    public void envelopeCreationTest()
    {
        IsotopicEnvelope env = new IsotopicEnvelope(this.traces, 0);
        
        // charge state should be 2
        Assert.assertEquals(2, env.chargeState);
        
        // intensity sum should be 12
        Assert.assertEquals(12.0, env.intensitySum, DELTA);
        
        // expected relative intensities
        Assert.assertArrayEquals(new double[]{1.0/12.0, 3.0/12.0, 4.0/12.0, 3.0/12.0, 1.0/12.0}, env.relativeIntensities, DELTA);
        
        Assert.assertEquals(1.02, env.monoisotopicMZ, DELTA);
    }
}
