/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.TraceCluster;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.Graph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.junit.Test;
import org.junit.Assert;

/**
 *
 * @author kyle
 */
public class DeclusterTest {
        
    @Test
    public void removeTraceEdgesTest()
    {
        // instantiate graph and trace vertices
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.0, 0f, 0f, 0.0);
        
        // add vertices to graph
        graph.addVertex(t1);
        graph.addVertex(t2);
        graph.addVertex(t3);
        graph.addVertex(t4);
        
        // add edges
        graph.addEdge(t1, t2);
        graph.addEdge(t1, t3);
        graph.addEdge(t1, t4);
        DefaultWeightedEdge e4 = graph.addEdge(t2, t3);
        DefaultWeightedEdge e5 = graph.addEdge(t2, t4);
        DefaultWeightedEdge e6 = graph.addEdge(t3, t4);
        
        // create cluster
        TraceCluster cluster = new TraceCluster(graph.vertexSet(),graph);
        
        cluster.removeTracesEdges(t1);
        
        Set<DefaultWeightedEdge> expectedRemainingEdges = new HashSet<>();
        expectedRemainingEdges.add(e4); expectedRemainingEdges.add(e5); expectedRemainingEdges.add(e6);
        
        // assert that each remaining edge is expected
        cluster.edges.stream().forEach(e -> {
            Assert.assertTrue(expectedRemainingEdges.stream().anyMatch(ee -> e.equals(ee)));
            
        });
        
    }
    
    @Test
    public void nextMinTraceTest()
    {
        // instantiate graph and trace vertices
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.0, 0f, 0f, 0.0);
        
        // add vertices to graph
        graph.addVertex(t1);
        graph.addVertex(t2);
        graph.addVertex(t3);
        graph.addVertex(t4);
        
        // add edges
        graph.addEdge(t1, t2);
        graph.addEdge(t1, t3);
        graph.addEdge(t1, t4);
        
        // create cluster
        TraceCluster cluster = new TraceCluster(graph.vertexSet(),graph);
        
        // next minimum
        cluster.nextMinTrace();
        
        // did we get the expected next min?
        Assert.assertEquals(t3, cluster.minMzTrace);
    }
    
    @Test
    public void nextMaxTraceTest()
    {
        // instantiate graph and trace vertices
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.5, 0f, 0f, 0.0);
        
        // add vertices to graph
        graph.addVertex(t1);
        graph.addVertex(t2);
        graph.addVertex(t3);
        graph.addVertex(t4);
        
        // add edges
        graph.addEdge(t1, t2);
        graph.addEdge(t1, t3);
        graph.addEdge(t1, t4);
        
        // create cluster
        TraceCluster cluster = new TraceCluster(graph.vertexSet(),graph);
        
        // next minimum
        cluster.nextMaxTrace();
        
        // did we get the expected next max?
        Assert.assertEquals(t2, cluster.maxMzTrace);
    }
    
    @Test
    public void completedChecksTest()
    {
        // artificial extractions graph
        Graph<IsotopeTrace,DefaultEdge> extractions = new SimpleGraph<>(DefaultEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 2.0, 0f, 0f, 0.0);
        extractions.addVertex(t1); extractions.addVertex(t2); 
        extractions.addVertex(t3);
        extractions.addEdge(t1, t2);
        extractions.addEdge(t2, t3);
        
        // trace 2 is complete
        Assert.assertTrue(TraceClusterer.isComplete(t2, extractions));
        
        // traces 1 and 3 are incomplete
        Assert.assertTrue(!TraceClusterer.isComplete(t1, extractions));
        Assert.assertTrue(!TraceClusterer.isComplete(t3, extractions));
        
        // trace 1 is a completed min
        Assert.assertTrue(TraceClusterer.isCompleteExtreme(t2, extractions, true));
        
        // trace 3 is a completed max
        Assert.assertTrue(TraceClusterer.isCompleteExtreme(t3, extractions, false));
    }
    
    //******************************************|
    //          DISSECT CLUSTER TESTS           |
    //******************************************|
    
    @Test
    public void dissectClusterTrivialTest()
    {
        // trivial case:
        //  cluster has only one envelope
        
        // instantiate graph with trace vertices
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.5, 0f, 0f, 0.0);
        IsotopeTrace t5 = new IsotopeTrace(5, 3.0, 0f, 0f, 0.0);
        graph.addVertex(t1); graph.addVertex(t2); graph.addVertex(t3); graph.addVertex(t4); graph.addVertex(t5);
        
        // add edges and set weights
        DefaultWeightedEdge e1 = graph.addEdge(t1, t2); graph.setEdgeWeight(e1, .5);
        DefaultWeightedEdge e2 = graph.addEdge(t2, t3); graph.setEdgeWeight(e2, .6);
        DefaultWeightedEdge e3 = graph.addEdge(t3, t4); graph.setEdgeWeight(e3, .7);
        DefaultWeightedEdge e4 = graph.addEdge(t4, t5); graph.setEdgeWeight(e4, .6);
        
        // instantiate cluster with all traces in graph
        Set<IsotopeTrace> traces = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t1, t2, t3, t4, t5}));
        TraceCluster cluster = new TraceCluster(traces, graph);
        
        // prepare extractions graph
        Graph<IsotopeTrace,DefaultEdge> extractions = new SimpleGraph<>(DefaultEdge.class);
               
        // perform dissection
        new TraceClusterer().dissectCluster(cluster, extractions);
        
        // extract connected sets
        ConnectivityInspector<IsotopeTrace, DefaultEdge> inspect = new ConnectivityInspector<>(extractions);
        List<Set<IsotopeTrace>> connectedSets = inspect.connectedSets();
        
        // assert only 1 connected set
        Assert.assertEquals(1, connectedSets.size());
        
        // assert that the connected set matches the entire 
        Assert.assertEquals(traces, connectedSets.get(0));
    }
    
    @Test
    public void dissectClusterNonTrivialTest()
    {
        // instantiate non-trivial graph
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 1.75, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t5 = new IsotopeTrace(5, 2.25, 0f, 0f, 0.0);
        IsotopeTrace t6 = new IsotopeTrace(6, 2.75, 0f, 0f, 0.0);
        graph.addVertex(t1); graph.addVertex(t2); graph.addVertex(t3); 
        graph.addVertex(t4); graph.addVertex(t5); graph.addVertex(t6);
        
        // add edges and set weights
        DefaultWeightedEdge e12 = graph.addEdge(t1, t2); graph.setEdgeWeight(e12, .8); // env1
        DefaultWeightedEdge e13 = graph.addEdge(t1, t3); graph.setEdgeWeight(e13, .3);
        DefaultWeightedEdge e14 = graph.addEdge(t1, t4); graph.setEdgeWeight(e14, .3);
        DefaultWeightedEdge e15 = graph.addEdge(t1, t5); graph.setEdgeWeight(e15, .3);
        DefaultWeightedEdge e16 = graph.addEdge(t1, t6); graph.setEdgeWeight(e16, .3);
        DefaultWeightedEdge e23 = graph.addEdge(t2, t3); graph.setEdgeWeight(e23, .2); 
        DefaultWeightedEdge e24 = graph.addEdge(t2, t4); graph.setEdgeWeight(e24, .6); // env1
        DefaultWeightedEdge e25 = graph.addEdge(t2, t5); graph.setEdgeWeight(e25, .4);
        DefaultWeightedEdge e26 = graph.addEdge(t2, t6); graph.setEdgeWeight(e26, .4);
        DefaultWeightedEdge e34 = graph.addEdge(t3, t4); graph.setEdgeWeight(e34, .2);
        DefaultWeightedEdge e35 = graph.addEdge(t3, t5); graph.setEdgeWeight(e35, .7); // env2
        DefaultWeightedEdge e36 = graph.addEdge(t3, t6); graph.setEdgeWeight(e36, .2);
        DefaultWeightedEdge e45 = graph.addEdge(t4, t5); graph.setEdgeWeight(e45, .3);
        DefaultWeightedEdge e46 = graph.addEdge(t4, t6); graph.setEdgeWeight(e46, .2);
        DefaultWeightedEdge e56 = graph.addEdge(t5, t6); graph.setEdgeWeight(e56, .9); // env2
        
        // instantiate cluster with all traces in graph
        Set<IsotopeTrace> traces = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t1, t2, t3, t4, t5, t6}));
        TraceCluster cluster = new TraceCluster(traces, graph);
        
        // prepare extractions graph
        Graph<IsotopeTrace,DefaultEdge> extractions = new SimpleGraph<>(DefaultEdge.class);
        
        // perform dissection
        new TraceClusterer().dissectCluster(cluster, extractions);
        
        // expected returned sets
        Set<IsotopeTrace> expectedEnv1 = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t1, t2, t4}));
        Set<IsotopeTrace> expectedEnv2 = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t3, t5, t6}));
        
        // get connected sets from extractions graph
        ConnectivityInspector<IsotopeTrace, DefaultEdge> inspect = new ConnectivityInspector<>(extractions);
        List<Set<IsotopeTrace>> connectedSets = inspect.connectedSets();
        
        // assert that each connected set is expected
        connectedSets.stream().forEach(connSet -> Assert.assertTrue(connSet.equals(expectedEnv1) || connSet.equals(expectedEnv2)));
    }
    
    @Test
    public void dissectClusterExtremeTest()
    {
        // instantiate non-trivial graph
        SimpleWeightedGraph<IsotopeTrace,DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        
        // env 1
        IsotopeTrace t2 = new IsotopeTrace(2, 1.5, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 2.5, 0f, 0f, 0.0);
        
        // env 2
        IsotopeTrace t5 = new IsotopeTrace(5, 1.6, 0f, 0f, 0.0);
        IsotopeTrace t6 = new IsotopeTrace(6, 2.1, 0f, 0f, 0.0);
        IsotopeTrace t7 = new IsotopeTrace(7, 2.7, 0f, 0f, 0.0);
        
        // env 3
        IsotopeTrace t9 = new IsotopeTrace(9, 1.2, 0f, 0f, 0.0);
        IsotopeTrace t10 = new IsotopeTrace(10, 1.7, 0f, 0f, 0.0);
        IsotopeTrace t11 = new IsotopeTrace(11, 2.2, 0f, 0f, 0.0);
        
        // add vertices
        graph.addVertex(t2); graph.addVertex(t3); graph.addVertex(t4); graph.addVertex(t5); 
        graph.addVertex(t6); graph.addVertex(t7); graph.addVertex(t9); 
        graph.addVertex(t10); graph.addVertex(t11);
        
        // add edges and set weights
        DefaultWeightedEdge e23 = graph.addEdge(t2, t3); graph.setEdgeWeight(e23, .7); // env1
        DefaultWeightedEdge e24 = graph.addEdge(t2, t4); graph.setEdgeWeight(e24, .4); 
        DefaultWeightedEdge e25 = graph.addEdge(t2, t5); graph.setEdgeWeight(e25, .4); 
        DefaultWeightedEdge e26 = graph.addEdge(t2, t6); graph.setEdgeWeight(e26, .4);
        DefaultWeightedEdge e27 = graph.addEdge(t2, t7); graph.setEdgeWeight(e27, .4);
        DefaultWeightedEdge e29 = graph.addEdge(t2, t9); graph.setEdgeWeight(e29, .4);
        DefaultWeightedEdge e210 = graph.addEdge(t2, t10); graph.setEdgeWeight(e210, .4);
        DefaultWeightedEdge e211 = graph.addEdge(t2, t11); graph.setEdgeWeight(e211, .4);
        
        DefaultWeightedEdge e34 = graph.addEdge(t3, t4); graph.setEdgeWeight(e34, .7); // env1
        DefaultWeightedEdge e35 = graph.addEdge(t3, t5); graph.setEdgeWeight(e35, .4);
        DefaultWeightedEdge e36 = graph.addEdge(t3, t6); graph.setEdgeWeight(e36, .4);
        DefaultWeightedEdge e37 = graph.addEdge(t3, t7); graph.setEdgeWeight(e37, .4);
        DefaultWeightedEdge e39 = graph.addEdge(t3, t9); graph.setEdgeWeight(e39, .4);
        DefaultWeightedEdge e310 = graph.addEdge(t3, t10); graph.setEdgeWeight(e310, .4);
        DefaultWeightedEdge e311 = graph.addEdge(t3, t11); graph.setEdgeWeight(e311, .4);
        
        DefaultWeightedEdge e45 = graph.addEdge(t4, t5); graph.setEdgeWeight(e45, .2);
        DefaultWeightedEdge e46 = graph.addEdge(t4, t6); graph.setEdgeWeight(e46, .2);
        DefaultWeightedEdge e47 = graph.addEdge(t4, t7); graph.setEdgeWeight(e47, .2);
        DefaultWeightedEdge e49 = graph.addEdge(t4, t9); graph.setEdgeWeight(e49, .2);
        DefaultWeightedEdge e410 = graph.addEdge(t4, t10); graph.setEdgeWeight(e410, .2);
        DefaultWeightedEdge e411 = graph.addEdge(t4, t11); graph.setEdgeWeight(e411, .2);
        
        DefaultWeightedEdge e56 = graph.addEdge(t5, t6); graph.setEdgeWeight(e56, .7); // env2
        DefaultWeightedEdge e57 = graph.addEdge(t5, t7); graph.setEdgeWeight(e57, .4);
        DefaultWeightedEdge e59 = graph.addEdge(t5, t9); graph.setEdgeWeight(e59, .4);
        DefaultWeightedEdge e510 = graph.addEdge(t5, t10); graph.setEdgeWeight(e510, .4);
        DefaultWeightedEdge e511 = graph.addEdge(t5, t11); graph.setEdgeWeight(e511, .4);
        
        DefaultWeightedEdge e67 = graph.addEdge(t6, t7); graph.setEdgeWeight(e67, .7); // env2
        DefaultWeightedEdge e69 = graph.addEdge(t6, t9); graph.setEdgeWeight(e69, .4);
        DefaultWeightedEdge e610 = graph.addEdge(t6, t10); graph.setEdgeWeight(e610, .4);
        DefaultWeightedEdge e611 = graph.addEdge(t6, t11); graph.setEdgeWeight(e611, .4);
        
        DefaultWeightedEdge e79 = graph.addEdge(t7, t9); graph.setEdgeWeight(e79, .4);
        DefaultWeightedEdge e710 = graph.addEdge(t7, t10); graph.setEdgeWeight(e710, .4);
        DefaultWeightedEdge e711 = graph.addEdge(t7, t11); graph.setEdgeWeight(e711, .4);
        
        DefaultWeightedEdge e910 = graph.addEdge(t9, t10); graph.setEdgeWeight(e910, .7); // env3
        DefaultWeightedEdge e911 = graph.addEdge(t9, t11); graph.setEdgeWeight(e911, .4);
        
        DefaultWeightedEdge e1011 = graph.addEdge(t10, t11); graph.setEdgeWeight(e1011, .7); // env3
        
        // instantiate cluster with all traces in graph
        Set<IsotopeTrace> traces = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t2, t3, t4, t5, t6, t7, t9, t10, t11}));
        TraceCluster cluster = new TraceCluster(traces, graph);
        
        // prepare extractions graph
        Graph<IsotopeTrace,DefaultEdge> extractions = new SimpleGraph<>(DefaultEdge.class);
        
        // perform dissection
        new TraceClusterer().dissectCluster(cluster, extractions);
        
        // expected returned sets
        Set<IsotopeTrace> expectedEnv1 = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t2, t3, t4}));
        Set<IsotopeTrace> expectedEnv2 = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t5, t6, t7}));
        Set<IsotopeTrace> expectedEnv3 = new HashSet<>(Arrays.asList(new IsotopeTrace[]{t9, t10, t11}));
        
        // get connected sets from extractions graph
        ConnectivityInspector<IsotopeTrace, DefaultEdge> inspect = new ConnectivityInspector<>(extractions);
        List<Set<IsotopeTrace>> connectedSets = inspect.connectedSets();
        
        // assert that each connected set is expected
        connectedSets.stream().forEach(connSet -> Assert.assertTrue(connSet.equals(expectedEnv1) || connSet.equals(expectedEnv2) || connSet.equals(expectedEnv3)));
    }
    
    // the following tests are disabled because the functionality they test is
    // best suited for real world examples. 
    // That is, it would be SUPER tedious to create real-world-like data, so
    // its best to let real world data fill the role
    
    //@Test
    public void consistencyDeclusterTest1()
    {
        IsotopeTrace t1 = new IsotopeTrace(1, 1.0, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 2.0, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 3.0, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 3.25, 0f, 0f, 0.0);
        IsotopeTrace t5 = new IsotopeTrace(5, 3.5, 0f, 0f, 0.0);
        IsotopeTrace t6 = new IsotopeTrace(6, 3.75, 0f, 0f, 0.0);
        
        Set<IsotopeTrace> connSet = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3, t4, t5, t6}));
        
        Set<IsotopeTrace> expectedSet1 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3}));
        Set<IsotopeTrace> expectedSet2 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t4, t5, t6}));
        
        List<Set<IsotopeTrace>> consistentSets = new TraceClusterer().consistencyDecluster(connSet);
        
        Assert.assertEquals(2, consistentSets.size());
        consistentSets.stream().forEach(set -> Assert.assertTrue(set.equals(expectedSet1) || set.equals(expectedSet2)));
    }
    
    //@Test
    public void consistencyDeclusterTest2()
    {
        IsotopeTrace t1 = new IsotopeTrace(1, 0.9, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 1.92, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 3.04, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 3.23, 0f, 0f, 0.0);
        IsotopeTrace t5 = new IsotopeTrace(5, 3.5, 0f, 0f, 0.0);
        IsotopeTrace t6 = new IsotopeTrace(6, 3.74, 0f, 0f, 0.0);
        
        Set<IsotopeTrace> connSet = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3, t4, t5, t6}));
        
        Set<IsotopeTrace> expectedSet1 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3}));
        Set<IsotopeTrace> expectedSet2 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t4, t5, t6}));
        
        List<Set<IsotopeTrace>> consistentSets = new TraceClusterer().consistencyDecluster(connSet);
        
        Assert.assertEquals(2, consistentSets.size());
        consistentSets.stream().forEach(set -> Assert.assertTrue(set.equals(expectedSet1) || set.equals(expectedSet2)));
    }
    
    //@Test
    public void consistencyDeclusterTest3()
    {
        IsotopeTrace t1 = new IsotopeTrace(1, 0.9, 0f, 0f, 0.0);
        IsotopeTrace t2 = new IsotopeTrace(2, 1.92, 0f, 0f, 0.0);
        IsotopeTrace t3 = new IsotopeTrace(3, 3.04, 0f, 0f, 0.0);
        IsotopeTrace t4 = new IsotopeTrace(4, 3.23, 0f, 0f, 0.0);
        IsotopeTrace t5 = new IsotopeTrace(5, 3.5, 0f, 0f, 0.0);
        IsotopeTrace t6 = new IsotopeTrace(6, 3.74, 0f, 0f, 0.0);
        IsotopeTrace t7 = new IsotopeTrace(7, 4.74, 0f, 0f, 0.0);
        IsotopeTrace t8 = new IsotopeTrace(8, 5.73, 0f, 0f, 0.0);
        IsotopeTrace t9 = new IsotopeTrace(9, 6.72, 0f, 0f, 0.0);
        
        Set<IsotopeTrace> connSet = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3, t4, t5, t6, t7, t8, t9}));
        
        Set<IsotopeTrace> expectedSet1 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t1, t2, t3}));
        Set<IsotopeTrace> expectedSet2 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t4, t5, t6}));
        Set<IsotopeTrace> expectedSet3 = new HashSet<>(Arrays.asList(new IsotopeTrace[] {t7, t8, t9}));
        
        List<Set<IsotopeTrace>> consistentSets = new TraceClusterer().consistencyDecluster(connSet);
        
        Assert.assertEquals(3, consistentSets.size());
        consistentSets.stream().forEach(set -> Assert.assertTrue(set.equals(expectedSet1) || set.equals(expectedSet2) || set.equals(expectedSet3)));
    }
}
