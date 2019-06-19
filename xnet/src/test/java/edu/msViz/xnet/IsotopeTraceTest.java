/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.junit.Test;
import org.junit.Assert;

/**
 *
 * @author kyle
 */
public class IsotopeTraceTest {
    
    private static final double DELTA = 1e-15;
    
    private final List<Double> mzValues = new ArrayList<>(Arrays.asList(
            0.5, 1.0, 1.5,
            0.5, 1.0, 1.5,
            0.5, 1.0, 1.5,
            0.5, 1.0, 1.5,
            0.5, 1.0, 1.5,
            0.5, 1.0, 1.5
    ));
    
    private final List<Float> rtValues = new ArrayList<>(Arrays.asList(
            0.5f, 0.5f, 0.5f,
            1.0f, 1.0f, 1.0f,
            1.5f, 1.5f, 1.5f,
            2.0f, 2.0f, 2.0f,
            2.5f, 2.5f, 2.5f,
            3.0f, 3.0f, 3.0f
    ));
    
    private final List<Double> intensityValues = new ArrayList<>(Arrays.asList(
            0.5, 1.5, 0.5,
            1.0, 2.0, 1.0,
            1.5, 2.5, 1.5,
            2.0, 3.0, 2.0,
            1.5, 2.5, 1.5,
            1.0, 2.0, 1.0
    ));
    
    private final IsotopeTrace trace = new IsotopeTrace(0, mzValues, rtValues, intensityValues);
    
    @Test
    public void traceConstructionTest()
    {
        Map<Float,Double> expectedArc = new HashMap<>();
        
        expectedArc.put(0.5f, 1.5);
        expectedArc.put(1.0f, 2.0);
        expectedArc.put(1.5f, 2.5);
        expectedArc.put(2.0f, 3.0);
        expectedArc.put(2.5f, 2.5);
        expectedArc.put(3.0f, 2.0);
        
        // test scalars
        Assert.assertEquals(1.0, trace.centroidMZ, DELTA);
        Assert.assertEquals(0.5, trace.minRT, DELTA);
        Assert.assertEquals(3.0, trace.maxRT, DELTA);
        Assert.assertEquals(28.5, trace.intensitySum, DELTA);
        
        // test computed arc
        Assert.assertEquals(expectedArc, trace.arc);
    }
    
    @Test
    public void arcAlignmentTest()
    {
        Map<Float,Double> otherArc = new HashMap<>();
        otherArc.put(1.5f,1.0);
        otherArc.put(2.0f,1.5);
        otherArc.put(2.5f,2.0);
        otherArc.put(3.0f,2.5);
        otherArc.put(3.5f,2.0);
        otherArc.put(4.0f,1.5);
        otherArc.put(4.5f,1.0);
        
        double m = IsotopeTrace.ARC_FILL_VALUE;
        
        double[][] expectedAlignedArc = new double[][]{
            {1.5, 2.0, 2.5, 3.0, 2.5, 2.0, m, m, m},
            {m, m, 1.0, 1.5, 2.0, 2.5, 2.0, 1.5, 1.0}
        };
        
        double[][] actualAlignedArc = this.trace.alignArcs(otherArc);
        
        Assert.assertArrayEquals(expectedAlignedArc, actualAlignedArc);
        
        PearsonsCorrelation corr = new PearsonsCorrelation();
        
        double corr1 = corr.correlation(expectedAlignedArc[0], expectedAlignedArc[1]);
        
        double[] anotherArc = new double[]{1.0, 1.5, 2.0, 2.5, 2.0, 1.5};
        
        double corr2 = corr.correlation(Arrays.copyOf(expectedAlignedArc[0],6), anotherArc);
        
        // corr2 should be greater than corr1
        Assert.assertTrue(corr1 < corr2);
    }

}
