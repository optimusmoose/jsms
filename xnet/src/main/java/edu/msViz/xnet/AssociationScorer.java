/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;
import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.probability.ProbabilityAggregator;
import edu.msViz.xnet.dataTypes.ProbabilityBundle;
import edu.msViz.xnet.dataTypes.ProbabilityBundle.RV;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.eclipse.recommenders.jayes.BayesNet;
import org.eclipse.recommenders.jayes.BayesNode;
import org.eclipse.recommenders.jayes.inference.jtree.JunctionTreeAlgorithm;

/**
 * Scores the association between two traces by inferring
 * association likelihood using a bayes net
 * @author kyle
 */
public class AssociationScorer
{
    /**
     * correlation calculator
     */
    PearsonsCorrelation corr;

    /**
     * inference engine
     */
    JunctionTreeAlgorithm inferer;

    /**
     * the bayes net containing each probability table
     */
    private BayesNet bayesNet;

    /**
     * association probability table P(A)
     */
    private BayesNode associationNode;

    /**
     * separation probability table P(S|A)
     */
    private BayesNode separationNode;

    /**
     * correlation probability table P(C|A)
     */
    private BayesNode correlationNode;

    /**
     * Default constructor accepting probability aggregator
     * Constructs the bayes net based on the probability aggregator's
     * probability bundle
     * @param aggregator Probability aggregator responsible for populating the probability tables
     */
    public AssociationScorer(ProbabilityAggregator aggregator)
    {
        ProbabilityBundle probBundle = aggregator.getProbabilities();
        this.initBayesNet(probBundle);
        this.inferer = new JunctionTreeAlgorithm();
        this.inferer.setNetwork(this.bayesNet);
        this.corr = new PearsonsCorrelation();
    }

    /**
     * Scores the association between two traces by bayesian inference
     * @param t1 trace 1
     * @param t2 trace 2
     * @return association score
     */
    public double scoreAssociation(IsotopeTrace t1, IsotopeTrace t2)
    {
        // evidence collection
        Map<BayesNode,String> evidence = new HashMap<>();

        // trace separation
        double separation = Math.abs(t1.centroidMZ - t2.centroidMZ);

        evidence.put(this.separationNode, Utility.format(separation));

        // t1 aligned arc at [0], t2 aligned arc at [1]
        double[][] alignedArcs = t1.alignArcs(t2.arc);

        // arc correlation
        double correlation = this.corr.correlation(alignedArcs[0], alignedArcs[1]);
        // TODO: "correlation" is NaN when either arc has not enough variance.
        // this is the case in e.g. profiled data when a trace has more than one point
        // but they all have the same RT value. Try to find another way to score
        // this correlation, but for now return 0 to avoid crashes.
        if (Double.isNaN(correlation)) {
            return 0;
        }
        evidence.put(this.correlationNode, Utility.format(correlation));

        // set the evidence!
        this.inferer.setEvidence(evidence);

        // assoc = false -> [0]; assoc = true -> [1]
        double[] associationBelief = inferer.getBeliefs(this.associationNode);

        //System.out.print(separation + "," + correlation + ",");

        // return likelihood of being associated
        return associationBelief[1];
    }

    /**
     * Initializes the bayes net according to the probability bundle
     * @param bundle probability bundle containing variables' outcomes and probabilities
     */
    private void initBayesNet(ProbabilityBundle bundle)
    {
        // init bayes net
        this.bayesNet = new BayesNet();

        // create, configure and populate association node
        this.associationNode = this.bayesNet.createNode("Association");
        this.associationNode.addOutcomes("false","true");
        double[] bprobs = bundle.getProbabilities(RV.ASSOCIATION);
        this.associationNode.setProbabilities(bprobs);

        // create, configure and populate separation node
        this.separationNode = this.bayesNet.createNode("Separation");
        this.separationNode.addOutcomes(Utility.floatArrToStringArr(bundle.separationOutcomes));
        this.separationNode.setParents(Arrays.asList(this.associationNode));
        double[] sprobs = bundle.getProbabilities(RV.SEPARATION);
        this.separationNode.setProbabilities(sprobs);

        // create, configure and populate correlation node
        this.correlationNode = this.bayesNet.createNode("Correlation");
        this.correlationNode.addOutcomes(Utility.floatArrToStringArr(bundle.correlationOutcomes));
        this.correlationNode.setParents(Arrays.asList(this.associationNode));
        double[] cprobs = bundle.getProbabilities(RV.CORRELATION);
        this.correlationNode.setProbabilities(cprobs);

    }

}
