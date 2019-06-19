/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.TraceGrid;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.Assert;


/**
 * TraceGrid tests
 * @author kyle
 */
public class TraceGridTest
{
    
    //traceID, mz, minRT, maxRT, intensitysum
    private final IsotopeTrace[] traces = 
    {   
        new IsotopeTrace(1, 0.0, 2f, 3f, 0.0),
        new IsotopeTrace(2, 6.0, 0f, 1f, 0.0),
        new IsotopeTrace(3, .5, 2.2f, 2.8f, 0.0),
        new IsotopeTrace(4, 1.1, 2.3f, 2.7f, 0.0),
        new IsotopeTrace(5, 1.7, 2.4f, 2.7f, 0.0),
        new IsotopeTrace(6, 5.5, 0.2f, 0.7f, 0.0),
        new IsotopeTrace(7, 5.0, 0.3f, 0.6f, 0.0),
        new IsotopeTrace(8, 4.3, 0.3f, 0.6f, 0.0),
        new IsotopeTrace(9, 1.7, 0f, 0.9f, 0.0),
        new IsotopeTrace(10, 2.7, 1.2f, 1.8f, 0.0),
    };
    
    private final TraceGrid grid = new TraceGrid(Arrays.asList(traces));
    
    public TraceGridTest() {}
    
    @Test
    public void testGetIndexes() throws Exception
    {
        
        // coords(1) = {0,4,5}
        Assert.assertArrayEquals(new int[]{0,4,5}, grid.getTraceIndexes(traces[0]));
        
        // coords(2) = {5,0,1}
        Assert.assertArrayEquals(new int[]{5,0,1}, grid.getTraceIndexes(traces[1]));
        
        // coords(3) = {0,4,5}
        Assert.assertArrayEquals(new int[]{0,4,5}, grid.getTraceIndexes(traces[2]));
        
        // coords(4) = {1,4,5}
        Assert.assertArrayEquals(new int[]{1,4,5}, grid.getTraceIndexes(traces[3]));
    }
    
    @Test
    public void testGetNeighbors() throws Exception
    {
        // neighbs(1) = {3,4,5}
        Set<IsotopeTrace> neighbs = new HashSet<>(Arrays.asList(traces[2],traces[3],traces[4]));
        Assert.assertEquals(neighbs, this.grid.getNeighbors(traces[0]));
        
        // neighbs(6) = {2,7}
        neighbs = new HashSet<>(Arrays.asList(traces[1],traces[6]));
        Assert.assertEquals(neighbs, this.grid.getNeighbors(traces[5]));
        
        // neighbs(8) = {7}
        neighbs = new HashSet<>(Arrays.asList(traces[6]));
        Assert.assertEquals(neighbs, this.grid.getNeighbors(traces[7]));
        
        // neighbs(9) = {}
        neighbs = new HashSet<>();
        Assert.assertEquals(neighbs, this.grid.getNeighbors(traces[8]));
    }
    
    
}
