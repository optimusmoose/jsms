/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.TraceCluster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.junit.Test;

/**
 *
 * @author kyle
 */
public class ClusteringTest 
{
    public IsotopeTrace A = new IsotopeTrace(0, 1.0, 1f, 3f, 0.0);
    public IsotopeTrace B = new IsotopeTrace(1, 2.0, 2f, 4f, 0.0);
    public IsotopeTrace C = new IsotopeTrace(2, 3.0, 1f, 5f, 0.0);
    public IsotopeTrace D = new IsotopeTrace(3, 4.0, 2f, 3f, 0.0);
    public IsotopeTrace E = new IsotopeTrace(4, 5.0, 10f, 11f, 0.0);
    public IsotopeTrace F = new IsotopeTrace(5, 6.0, 2f, 3f, 0.0);
    public IsotopeTrace G = new IsotopeTrace(6, 7.0, 0f, 0f, 0.0);
    public IsotopeTrace H = new IsotopeTrace(7, 8.0, 0f, 0f, 0.0);
    
    public DefaultWeightedEdge edge1 = new DefaultWeightedEdge();
    public DefaultWeightedEdge edge2 = new DefaultWeightedEdge();
    public DefaultWeightedEdge edge3 = new DefaultWeightedEdge();

    // create edges graph
    SimpleWeightedGraph<IsotopeTrace, DefaultWeightedEdge> edges = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
    ConnectivityInspector<IsotopeTrace,DefaultWeightedEdge> conn;
    
    public ClusteringTest()
    {
        
        
        //TODO add weights to edges
        edges.addVertex(A); edges.addVertex(B); edges.addVertex(C); 
        edges.addVertex(D); edges.addVertex(E); edges.addVertex(F);
        edges.addVertex(G); edges.addVertex(H);
        edges.addEdge(A, B, new DefaultWeightedEdge());
        edges.addEdge(C, D, new DefaultWeightedEdge());
        edges.addEdge(E, F, edge1);
        edges.addEdge(F, G, edge2);
        edges.addEdge(G, H, edge3);
        
        // set edge weights for testing component (E,F,G,H)
        edges.setEdgeWeight(edge1, 1);
        edges.setEdgeWeight(edge2, 2);
        edges.setEdgeWeight(edge3, 3);
        
        conn = new ConnectivityInspector<>(edges);
    }
    
    @Test
    public void connectedSetsTest()
    {
        
        // extract connected sets
        
        List<Set<IsotopeTrace>> connectedSets = conn.connectedSets();
        
        // expected connected sets
        Set<IsotopeTrace> connAB = new HashSet<>();
        connAB.add(A); connAB.add(B);
        Set<IsotopeTrace> connCD = new HashSet<>();
        connCD.add(C); connCD.add(D);
        Set<IsotopeTrace> connEFGH = new HashSet<>();
        connEFGH.add(E); connEFGH.add(F); connEFGH.add(G); connEFGH.add(H);
        
        // collection of expected connected sets
        List<Set<IsotopeTrace>> expectedSets = new ArrayList<>();
        expectedSets.add(connAB); expectedSets.add(connCD); expectedSets.add(connEFGH);
        
        // ensure each actual set matches an expected set
        // order can't be anticipated, so must perform a search in the expected sets
        for(Set<IsotopeTrace> actualSet : connectedSets)
        {
            boolean hasMatch = false;
            Set<IsotopeTrace> toRemove = null;
            for(Set<IsotopeTrace> expectedSet : expectedSets)
            {
                if(actualSet.equals(expectedSet))
                {
                    hasMatch = true;
                    toRemove = expectedSet;
                    break;
                }
            }
            
            // assert that the actual set had a matching expected set 
            Assert.assertTrue(hasMatch);
            
            // remove the matched expected set from consideration
            if(toRemove != null) expectedSets.remove(toRemove);
        }
        
        // assert that we've matched all expected sets
        Assert.assertTrue(expectedSets.isEmpty());
    }
    
    @Test
    public void connectedSetToClusterTest()
    {
        // construct a trace using the larget set (size 4) 
        
        Set<IsotopeTrace> largerSet = null;
        List<Set<IsotopeTrace>> connSets = this.conn.connectedSets();
        for(Set<IsotopeTrace> connSet : connSets)
        {
            if(connSet.size() == 4)
            {
                largerSet = connSet;
                break;
            }
        }
        TraceCluster cluster = new TraceCluster(largerSet, edges);

        // test edge order to be descending
        Double previousWeight = null;
        while(cluster.hasNext())
        {
            IsotopeTrace[] curTraces = cluster.next();
            DefaultWeightedEdge curEdge = edges.getEdge(curTraces[0], curTraces[1]);
            double weight = edges.getEdgeWeight(curEdge);
            if(previousWeight == null)
            {
                previousWeight = weight;
            }
            else
            {
                Assert.assertTrue(weight < previousWeight);
                previousWeight = weight;
            }
        }
        
        // test min trace
        Assert.assertEquals(E, cluster.minMzTrace);
        
        // test max trace
        Assert.assertEquals(H, cluster.maxMzTrace);
        
    }
    
    @Test
    public void needsComparisonTest()
    {
        TraceClusterer tc = new TraceClusterer();
        Assert.assertTrue(tc.needsComparison(A, B));
        Assert.assertTrue(tc.needsComparison(B, A));
        Assert.assertTrue(tc.needsComparison(C, D));
        Assert.assertTrue(tc.needsComparison(D, C));
        Assert.assertTrue(!tc.needsComparison(F, E));
        Assert.assertTrue(!tc.needsComparison(E, F));
        Assert.assertTrue(!tc.needsComparison(B, F));
        Assert.assertTrue(!tc.needsComparison(F, B));
    }
    
    
}
