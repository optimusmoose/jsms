/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.dataTypes.TraceCluster;
import edu.msViz.xnet.dataTypes.*;
import edu.msViz.xnet.probability.BayesianProbability;
import edu.msViz.xnet.probability.ProbabilityAggregator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

/**
 * Clusters a set of isotope traces into isotopic envelopes
 * based on bayesian inference by a bayes net capturing
 * the latent properties of isotopic envelopes
 * @author kyle
 */
public class TraceClusterer 
{

    private static final Logger LOGGER = Logger.getLogger(TraceClusterer.class.getName());
    
    // maximum charge to consider for trace separation within envelope
    // trace separation = 1/z, z in {1..max charge}
    public static final int MAX_CHARGE = 5;
    
    // maximum separation within which to perform comparison between two traces
    public static final float MAX_SEPARATION = 1.1f;
    
    // envelope ID incrementer
    private int curEnvelopeID = 1;
    
    
    //***********************************************|
    //                     DRIVER                    |
    //***********************************************|
    
    /**
     * Clusters the list of isotope traces into isotopic envelopes according to 
     * the given probability model
     * @param traces traces to cluster
     * @param model the probabilistic model to use in scoring trace association
     * @param filePath (optional) probability file to use in scoring
     * @return list of isotopic envelopes
     */
    public List<IsotopicEnvelope> clusterTraces(List<IsotopeTrace> traces, ProbabilityAggregator.PROB_MODEL model, String filePath)
    {
        LOGGER.log(Level.INFO, "[1/3] Beginning trace clustering on " + traces.size() + " traces");
        List<TraceCluster> preliminaryClusters = STEP1_CLUSTER(traces, model, filePath);
        
        LOGGER.log(Level.INFO, "[2/3] Deconvolving " + preliminaryClusters.size() + " preliminary clusters");
        List<Set<IsotopeTrace>> deconvolved = STEP2_DECONVOLVE(preliminaryClusters);
        
        LOGGER.log(Level.INFO, "[3/3] Consistency pass on " + deconvolved.size() + " deconvolved clusters");
        return STEP3_CONSISTENCY(deconvolved);
    }
    
    //***********************************************|
    //                 STEP 1: CLUSTER               |
    //***********************************************|
    
    /**
     * creates clusters of traces by creating scored associations between 
     * neighborhoods traces in the trace set
     * @param traces set of traces to cluster
     * @param model scoring model choice
     * @param filePath (optional) path to probability file
     * @return collection of trace clusters
     */
    private List<TraceCluster> STEP1_CLUSTER(List<IsotopeTrace> traces, ProbabilityAggregator.PROB_MODEL model, String filePath)
    {
        // prepare association scorer
        AssociationScorer scorer = new AssociationScorer(ProbabilityAggregator.probAggFactory(model, filePath));

        // arrange traces into grid
        TraceGrid traceGrid = new TraceGrid(traces);
        
        // associations collection
        SimpleWeightedGraph<IsotopeTrace, DefaultWeightedEdge> associations = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        
        // sort traces by mz
        traces.sort(Comparator.comparing(trace -> trace.centroidMZ));
        
        // iterate traces that have more than one scan, 
        // evaluating association between all valid neighbors
        traces.stream().forEach(trace ->
        {
            // process only traces that span more than 1 scan
            // semantically, a 1 scan trace is suspicious
            // logically, we cannot compute correlations with a 1 scan trace
            if(trace.arc.size() > 1)
            {
                // evaluate associations between all nearby traces
                TreeSet<IsotopeTrace> neighbors = traceGrid.getNeighbors(trace);   
                neighbors.stream().forEach(neighbor ->
                {
                    // additionally, neighbor cannot have a size of 1
                    if(neighbor.arc.size() > 1)
                    {
                        // if traces' separation <= 1.1 and traces have RT overlap
                        if(this.needsComparison(trace, neighbor))
                        {
                            //System.out.print(trace.traceID + ", " + neighbor.traceID + ", ");
                            // score association, store in associations container graph
                            double associationScore = scorer.scoreAssociation(trace, neighbor);
                            //System.out.println(associationScore);
                            associations.addVertex(trace);
                            associations.addVertex(neighbor);
                            DefaultWeightedEdge edge = associations.addEdge(trace, neighbor);
                            double roundedAssociationScore = Math.round(associationScore * 10);
                            associations.setEdgeWeight(edge, roundedAssociationScore);
                        }
                    }
                });
            }
            
            // remove the trace from the grid (don't double process!)
            traceGrid.removeTrace(trace);
        });
        
        // retrieve connected sets
        ConnectivityInspector<IsotopeTrace, DefaultWeightedEdge> connectivity = new ConnectivityInspector<>(associations);
        List<Set<IsotopeTrace>> connectedSets = connectivity.connectedSets();
        
        // convert connected sets to trace clusters, return as list
        return connectedSets.stream().map(connSet -> new TraceCluster(connSet, associations)).collect(Collectors.toList());
        
    }
    
    //***********************************************|
    //               STEP 2: DECLUSTER               |
    //***********************************************|
    
    /**
     * Declusters each trace cluster by splitting clusters into subgraphs
     * representing isotopic envelopes
     * @param clusters trace clusters to decluster
     * @return list of resulting isotopic envelopes
     */
    private List<Set<IsotopeTrace>> STEP2_DECONVOLVE(List<TraceCluster> clusters)
    {   
        Graph<IsotopeTrace,DefaultEdge> extractions = new SimpleGraph<>(DefaultEdge.class);
        
        // dissect each cluster by extracting envelope edges
        clusters.stream().forEach(cluster -> this.dissectCluster(cluster, extractions));

        // extract connected sets
        ConnectivityInspector<IsotopeTrace,DefaultEdge> connectivityInspector = new ConnectivityInspector<>(extractions);
        return connectivityInspector.connectedSets();
    }
    
    /**
     * Dissects the trace cluster into the set of edges belonging to isotopic envelopes,
     * placing the resulting edges/vertices into envelopeEdges, an edge container
     * @param cluster cluster to dissect
     * @param extractions edges/vertices container for extracted components
     */
    public void dissectCluster(TraceCluster cluster, Graph<IsotopeTrace,DefaultEdge> extractions)
    {   
        // iterate through edges by descending score order
        // edge's source and target are return per iteration
        while(cluster.hasNext())
        {
            IsotopeTrace[] vertexPair = cluster.next();
            
            // only collect the extracted edge if it is the first 
            // extraction on the given side of each trace
            if(!this.createsDoubleExtraction(vertexPair, extractions))
            {
                // insert extracted edge/vertices into container graph
                extractions.addVertex(vertexPair[0]);
                extractions.addVertex(vertexPair[1]);
                extractions.addEdge(vertexPair[0], vertexPair[1]);
            }
        }
    }
    
    //***********************************************|
    //             STEP 3: CONSISTENCY               |
    //***********************************************|
       
    /**
     * Furthers splits deconvolved trace sets into envelope consistent sets
     * @param deconvolved trace sets that have been deconvolved by step 2
     * @return list of isotopic envelopes comprising envelope consistent trace sets
     */
    private List<IsotopicEnvelope> STEP3_CONSISTENCY(List<Set<IsotopeTrace>> deconvolved)
    {
        
        // results set
        List<Set<IsotopeTrace>> consistentSets = new ArrayList<>();
        
        // process each deconvolved set, decluster into envelope consistent sets
        for(Set<IsotopeTrace> connSet : deconvolved)
        {
            consistentSets.addAll(this.consistencyDecluster(connSet));
        }
                
        // convert consistent sets to isotopic envelopes
        return consistentSets.stream().map(connSet -> new IsotopicEnvelope(connSet,this.curEnvelopeID++)).collect(Collectors.toList());
    }
    
     /**
     * Further declusters a connected set to a collection of trace sets consistent 
     * with the properties of isotopic envelopes: each charge 
     * @param connSet connected set to decluster
     * @return list of envelope consistent trace sets, formed by decomposing connected trace set
     */
    public List<Set<IsotopeTrace>> consistencyDecluster(Set<IsotopeTrace> connSet)
    {
        // results set
        List<Set<IsotopeTrace>> consistentSets = new ArrayList<>();
        
        // container to accumulate each consistent set
        Set<IsotopeTrace> curConsistentSet = new HashSet<>();

        // tracking variables
        Integer curCharge = null;
        IsotopeTrace previousTrace = null;
        IsotopeTrace smallestTraceInSet = null;

        // iterate the conn set in ascending mz order
        TreeSet<IsotopeTrace> orderedConnSet = new TreeSet<>(Comparator.comparing(t -> t.centroidMZ));
        orderedConnSet.addAll(connSet);
        for(IsotopeTrace trace : orderedConnSet)
        {
            // first check. have we seen a trace yet?
            if(previousTrace == null)
            {
                // no. start new set
                curConsistentSet.add(trace);
            }
            else
            {
                // yes. have we set the current set's charge?
                if(curCharge == null)
                {
                    // no. is the current trace's apex consistent with the set? 
                    if(this.isApexConsistent(smallestTraceInSet, trace))
                    {
                        // yes. is the charge state valid?
                        int newCharge = (int)Math.round(1.0 / (trace.centroidMZ - previousTrace.centroidMZ));
                        if(this.isValidCharge(newCharge))
                        {
                            // yes. collect the trace and set the current charge
                            curConsistentSet.add(trace);
                            curCharge = newCharge;
                        }
                    }
                    else
                    {
                        // no. start new set
                        curConsistentSet = new HashSet<>();
                        smallestTraceInSet = null;
                        curConsistentSet.add(trace);
                    }
                }
                else
                {
                    int newCharge = (int)Math.round(1.0 / (trace.centroidMZ - previousTrace.centroidMZ));
                    // yes. is the current trace's apex consistent with the set
                    if(this.isApexConsistent(smallestTraceInSet, trace) && this.isChargeConsistent(curCharge, newCharge))
                    {
                        // yes. collect the current trace
                        curConsistentSet.add(trace);
                    }
                    else
                    {
                        // no. collect the current set, start a new set
                        consistentSets.add(curConsistentSet);
                        curConsistentSet = new HashSet<>();
                        curConsistentSet.add(trace);
                        smallestTraceInSet = null;
                        curCharge = null;
                    }
                }
            }
            
            // always update previous trace and smallest trace in set (null safe on smallestTraceInSet)
            previousTrace = trace;
            smallestTraceInSet = this.smallerTraceByRT(trace, smallestTraceInSet);
        }
        
        // collect the final consistent set
        consistentSets.add(curConsistentSet);
        
        return consistentSets;
    }
    
    //***********************************************|
    //                   HELPERS                     |
    //***********************************************|
    
    private boolean isApexConsistent(IsotopeTrace boundsTrace, IsotopeTrace candidateTrace)
    {
        float candidateTraceApexRT = candidateTrace.getMaxIntensityRT();
        return candidateTraceApexRT >= boundsTrace.minRT && candidateTraceApexRT <= boundsTrace.maxRT;
    }
    
    /**
     * Returns the smaller of the two traces by RT
     * @param t1 trace 1
     * @param t2OrNull trace 2 (null allowed)
     * @return the smaller trace by RT
     */
    private IsotopeTrace smallerTraceByRT(IsotopeTrace t1, IsotopeTrace t2OrNull)
    {
        if(t2OrNull == null)
            return t1;
        else
        {
            float t1RTLength = t1.maxRT - t1.minRT;
            float t2RTLength = t2OrNull.maxRT - t2OrNull.minRT;
            return t1RTLength <= t2RTLength ? t1 : t2OrNull;
        }
    }
    
    /**
     * Checks to see if the two traces are in a position that requires comparison
     * i.e. they are within 1.1 mz and have RT overlap
     * @param t1 trace 1
     * @param t2 trace 2
     * @return True if satisify comparison criteria, else false
     */
    public boolean needsComparison(IsotopeTrace t1, IsotopeTrace t2)
    {
        boolean hasSmallEnoughSeparation = Math.abs(t1.centroidMZ - t2.centroidMZ) <= MAX_SEPARATION;
        boolean hasRtOverlap = t1.minRT <= t2.maxRT && t2.minRT <= t1.maxRT;
        return hasSmallEnoughSeparation && hasRtOverlap;
    }
    
    /**
     * Checks whether a charge state is valid given the max charge configuration
     * @param chargeState charge state to validate
     * @return true if charge state between 1 and max charge (inclusive both ends)
     */
    private boolean isValidCharge(int chargeState)
    {
        return chargeState >= 1 && chargeState <= MAX_CHARGE;
    }
    
    /**
     * Checks if the a current and previous inter-trace values
     * are consistent with the properties of an isotopic envelope:
     *  1) equal charge state
     *  2) no valleys (disabled)
     * @param previousCharge previous inter trace charge state
     * @param currentCharge current inter trace charge state
     * @param previousIntensityDelta (disabled)
     * @param currentIntensityDelta (disabled)
     * @return true if previous and 
     */
    private boolean isChargeConsistent(int previousCharge, int currentCharge) //, double previousIntensityDelta, double currentIntensityDelta)
    {
        boolean sameCharge = previousCharge == currentCharge;
        //boolean isValley = (previousIntensityDelta < 0) && (currentIntensityDelta > 0);
        return sameCharge; //&& !isValley;
    }
    
    /**
     * Checks if a trace has an extraction on its left (lesser mz) and its right (greater mz)
     * @param trace trace subjected to left and right extractions test
     * @param extractions current cluster's extractions
     * @return True if trace has a left- and right-hand extraction
     */
    public static boolean isComplete(IsotopeTrace trace, Graph<IsotopeTrace,DefaultEdge> extractions)
    {
        return hasExtractionToSide(trace,extractions,true,true);
    }
    
    /**
     * Tests whether the given trace has an extraction on the left, or right, or both
     * @param trace trace to check 
     * @param extractions current set of extracted edges
     * @param checkLeft if true, checks on the lhs
     * @param checkRight if true, checks on the rhs
     * @return true if trace has an extraction to the specified side(s)
     */
    public static boolean hasExtractionToSide(IsotopeTrace trace, Graph<IsotopeTrace,DefaultEdge> extractions, boolean checkLeft, boolean checkRight)
    {
        boolean hasLeft = false;
        boolean hasRight = false;
        
        // explore each of the extraction edges neighboring the trace
        for(DefaultEdge edge : extractions.edgesOf(trace))
        {
            // get the edge's vertices
            IsotopeTrace source = extractions.getEdgeSource(edge);
            IsotopeTrace target = extractions.getEdgeTarget(edge);
            
            // resolve which trace is the other trace
            IsotopeTrace otherTrace = trace.equals(source) ? target : source;
            
            if(otherTrace.centroidMZ > trace.centroidMZ)
                hasRight = true;
            if(otherTrace.centroidMZ < trace.centroidMZ)
                hasLeft = true;
        }
        
        if(checkLeft && checkRight)
            return hasLeft && hasRight;
        else if(checkLeft)
            return hasLeft;
        else
            return hasRight;
    }
    
    /**
     * Checks if a trace is a completed extreme. That is, 
     * is the min mz trace and has a righthand (greater mz) association or
     * is the max mz trace and has a lefthand (lesser mz) association 
     * @param trace trace to check
     * @param extractions graph containing all extracted associations
     * @param isMin true if trace is min trace, false if trace is max trace
     * @return True if meets the criteria for being a completed extreme
     */
    public static boolean isCompleteExtreme(IsotopeTrace trace, Graph<IsotopeTrace,DefaultEdge> extractions, boolean isMin)
    {
        // if the trace doesn't exist in the extractions graph
        // then it is not complete
        if(!extractions.containsVertex(trace))
            return false;
        
        // gather neighbors in extracted graph
        List<IsotopeTrace> extractedNeighbors = new ArrayList<>();
        extractions.edgesOf(trace).stream().forEach(edge -> {
            IsotopeTrace source = extractions.getEdgeSource(edge);
            IsotopeTrace target = extractions.getEdgeTarget(edge);
            extractedNeighbors.add(trace.equals(source) ? target : source);
        });
        
        // predicate to use when matching any neighboring trace 
        Predicate<IsotopeTrace> pred;
        
        // if the trace being processed is the min mz trace
        // look for greater mz traces
        if(isMin)         
            pred = n -> n.centroidMZ > trace.centroidMZ;
        
        // else look for lesser mz traces
        else
            pred = n -> n.centroidMZ < trace.centroidMZ;
        
        // return result of searching for a single matching neighbor trace
        return extractedNeighbors.stream().anyMatch(pred);
    }
    
    /**
     * Returns whether or not the proposed extracted edge, given by the trace pair, would create
     * a double extraction on the corresponding side of each trace
     * @param tracePair endpoints for proposed extraction edge
     * @param extractions current accepted extractions
     * @return true if the lhs trace does not have an existing rhs extraction AND the rhs trace does not 
     * have an existing lhs extraction
     */
    private boolean createsDoubleExtraction(IsotopeTrace[] tracePair, Graph<IsotopeTrace,DefaultEdge> extractions)
    {
        int lhsTraceIX = tracePair[0].centroidMZ < tracePair[1].centroidMZ ? 0 : 1;
        IsotopeTrace lhsTrace = tracePair[lhsTraceIX];
        IsotopeTrace rhsTrace = tracePair[(lhsTraceIX + 1) % 2];
        
        boolean lhsHasRhs = extractions.containsVertex(lhsTrace) && hasExtractionToSide(lhsTrace, extractions, false, true);
        boolean rhsHasLhs = extractions.containsVertex(rhsTrace) && hasExtractionToSide(rhsTrace, extractions, true, false);
        
        return lhsHasRhs || rhsHasLhs; 
    }
    
    //***********************************************|
    //                   TRAINING                    |
    //***********************************************|
    
    /**
     * Trains the probability model on the given trace set, outputting to the given file path.
     * If a probability file exists at the given filepath, the existing probability model is modified.
     * Otherwise, a new probability file is created.
     * @param traces input trace set on which to train
     * @param filePath path to the probability file, existing or new
     * @throws java.io.IOException
     */
    public void trainProbabilities(List<IsotopeTrace> traces, String filePath) throws IOException
    {
        new FrequentistProbability(traces, filePath);
    }
}
