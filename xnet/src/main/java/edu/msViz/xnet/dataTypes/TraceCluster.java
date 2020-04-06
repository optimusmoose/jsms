/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

/**
 * A connected component of isotope traces. Implements the Iterator interface
 * on a pair of IsotopeTraces (as an array); iterates in order of association score
 * between trace pairs.
 * @author kyle
 */
public class TraceCluster implements Iterator<IsotopeTrace[]>
{   
    /**
     * max heap of edges, with p-value = association score
     */ 
    public PriorityQueue<DefaultWeightedEdge> edges;
    
    /**
     * Contained traces in ascending mz order
     */
    PriorityQueue<IsotopeTrace> minMzTraces = new PriorityQueue<>(Comparator.comparing(trace -> trace.centroidMZ));
    
    /**
     * Contained traces in descending mz order
     */
    PriorityQueue<IsotopeTrace> maxMzTraces = new PriorityQueue<>(Comparator.comparing((IsotopeTrace trace) -> trace.centroidMZ).reversed());
    
    /**
     * minimum mz trace
     */ 
    public IsotopeTrace minMzTrace;
    
    /**
     * maximum mz trace
     */ 
    public IsotopeTrace maxMzTrace;
    
    /**
     * cluster's weighted graph
     * Trace nodes connected by association score weighted edges
     */
    private final SimpleWeightedGraph<IsotopeTrace, DefaultWeightedEdge> graph;
    
    /**
     * Builds the cluster from the set of traces, subset to the complete graph
     * @param connectedSet set of traces belonging to the cluster
     * @param associations entire graph from which the trace set is collected
     */
    public TraceCluster(Set<IsotopeTrace> connectedSet, 
            SimpleWeightedGraph<IsotopeTrace, DefaultWeightedEdge> associations)
    {        
        // subgraph corresponding to connected set
        // will be built throughout the routine
        this.graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        
        // comparator sorting edge entries by weight descending, then by trace separation ascending
        Comparator<DefaultWeightedEdge> edgeComparator = Comparator.comparing((DefaultWeightedEdge edge) -> graph.getEdgeWeight(edge)).reversed().thenComparing((DefaultWeightedEdge edge) -> Math.abs(graph.getEdgeSource(edge).centroidMZ - graph.getEdgeTarget(edge).centroidMZ));
        
        // p-queue ordered by edge comparator
        this.edges = new PriorityQueue<>(edgeComparator);
        
        Set<DefaultWeightedEdge> curEdges = new HashSet<>();
        
        // add each vertex to the subgraph, collect edges to add
        for(IsotopeTrace trace : connectedSet)
        {
            this.minMzTraces.add(trace);
            this.maxMzTraces.add(trace);
            this.graph.addVertex(trace);
            curEdges.addAll(associations.edgesOf(trace));
        }
        
        // add each edge to the subgraph nnd prioritize its weight
        curEdges.stream().forEach(edge -> 
        {
            this.graph.addEdge((IsotopeTrace)associations.getEdgeSource(edge), (IsotopeTrace)associations.getEdgeTarget(edge), edge);
            this.edges.add(edge);
        });

        this.minMzTrace = this.minMzTraces.poll();
        this.maxMzTrace = this.maxMzTraces.poll();
    }
    
    /**
     * Removes the edges connected to the given trace from the edge heap.
     * Essentially, this function removes a trace from consideration when extracting
     * edges belonging to an envelope.
     * @param trace trace whose edges are to be removed
     */
    public void removeTracesEdges(IsotopeTrace trace)
    {
        // attempt to remove each of the trace's edges from the heap
        this.edges.removeAll(this.graph.edgesOf(trace));
    }
    
    /**
     * Removes the current min trace from consideration and 
     * moves min trace cursor to next min trace
     */
    public void nextMinTrace()
    {
        this.removeTracesEdges(this.minMzTrace);
        this.minMzTrace = this.minMzTraces.poll();
    }
    
    /**
     * Removes the current max trace from consideration and 
     * moves max trace cursor to next max trace
     */
    public void nextMaxTrace()
    {
        this.removeTracesEdges(this.maxMzTrace);
        this.maxMzTrace = this.maxMzTraces.poll();
    }

    @Override
    public boolean hasNext() {
        return !this.edges.isEmpty();
    }

    @Override
    public IsotopeTrace[] next() {
        IsotopeTrace[] traces = new IsotopeTrace[2];
        DefaultWeightedEdge nextEdge = this.edges.poll();
        traces[0] = this.graph.getEdgeSource(nextEdge);
        traces[1] = this.graph.getEdgeTarget(nextEdge);
        return traces;
    }
}
