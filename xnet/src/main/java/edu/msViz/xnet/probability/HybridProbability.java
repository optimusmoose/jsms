/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.probability;

import edu.msViz.xnet.dataTypes.ProbabilityBundle;

/**
 * Hybrid probability that encapsulates both the bayesian and frequentist
 * probabilities and conjoins their resulting probability bundles
 * @author kyle
 */
public class HybridProbability extends ProbabilityAggregator
{
    /**
     * Bayesian probability aggregator
     */
    private final BayesianProbability bayesProbs = new BayesianProbability();
    
    /**
     * Frequentsit probability aggregator
     */
    private final FrequentistProbability freqProbs;
    
    /**
     * Default constructor, instructs frequentist probability component to use default probability file
     */
    public HybridProbability()
    {
        this.freqProbs = new FrequentistProbability();
    }
    
    /**
     * Constructor accepting a filepath to the user specified probability file.
     * Instructs the frequentist probability component to use the user specified file
     * @param filepath 
     */
    public HybridProbability(String filepath)
    {
        this.freqProbs = new FrequentistProbability(filepath);
    }
    
    @Override
    protected ProbabilityBundle initBundle() 
    {
        // individual probabilities
        ProbabilityBundle fBundle = this.freqProbs.initBundle();
        ProbabilityBundle bBundle = this.bayesProbs.initBundle();
        
        // combine the bundles
        bBundle.absorb(fBundle);
        
        return bBundle;
    }

    @Override
    protected void populateAssociationCounts(ProbabilityBundle bundle) {
        this.freqProbs.populateAssociationCounts(bundle);
        this.bayesProbs.populateAssociationCounts(bundle);
    }

    @Override
    protected void populateSeparationCounts(ProbabilityBundle bundle) {
        this.freqProbs.populateSeparationCounts(bundle);
        this.bayesProbs.populateSeparationCounts(bundle);
    }

    @Override
    protected void populateCorrelationCounts(ProbabilityBundle bundle) {
        this.freqProbs.populateCorrelationCounts(bundle);
        this.bayesProbs.populateCorrelationCounts(bundle);
    }
    
}
