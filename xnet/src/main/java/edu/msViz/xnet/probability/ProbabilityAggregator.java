/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.probability;

import edu.msViz.xnet.dataTypes.ProbabilityBundle;
import java.security.InvalidParameterException;

/**
 * Abstract class providing basic probability aggregation functions
 * and defining an interface for concrete implementations 
 * @author kyle
 */
public abstract class ProbabilityAggregator 
{
    /**
     * Probability models
     */
    public static enum PROB_MODEL {BAYESIAN, FREQUENTIST, HYBRID};
    
    /**
     * factory method for returning the selected probability aggregator
     * @param aggregatorChoice model selection
     * @param filePath (optional) probability file to use in frequentist/hybrid model
     * @return selected probability aggregator
     */
    public static ProbabilityAggregator probAggFactory(PROB_MODEL aggregatorChoice, String filePath)
    {
        switch(aggregatorChoice)
        {
            case BAYESIAN:
                return new BayesianProbability();
            case FREQUENTIST:
                return filePath == null ? new FrequentistProbability() : new FrequentistProbability(filePath);
            case HYBRID:
                return filePath == null ? new HybridProbability() : new HybridProbability(filePath);
                
            default:
                throw new InvalidParameterException("Invalid scorer selection.");
        }
    }
    
    /**
     * Constructs a ProbabilityBundle containing probability distributions
     * for separation, correlation and association RVs
     * @return ProbabilityBundle object
     */
    public ProbabilityBundle getProbabilities()
    {    
        // init probability bundle
        ProbabilityBundle probs = this.initBundle();
        
        // populate association counts in bundle
        this.populateAssociationCounts(probs);
        
        // populate separation counts in bundle
        this.populateSeparationCounts(probs);
        
        // populate correlation counts in bundle
        this.populateCorrelationCounts(probs);
        
        return probs;
    }
    
    /**
     * Instantiates a probability bundle and populates it
     * @return Populated probability bundle
     */
    protected abstract ProbabilityBundle initBundle();
    
    /**
     * Populates the association counts within the prob bundle
     * @param bundle bundle to populate
     */
    protected abstract void populateAssociationCounts(ProbabilityBundle bundle);
    
    /**
     * Populates the separation counts array in the probability bundle
     * according to the separation distributions
     * @param bundle bundle to populate
     */
    protected abstract void populateSeparationCounts(ProbabilityBundle bundle);
    
    /**
     * Populates the correlation count arrays within the probability bundle
     * @param bundle bundle to populate
     */
    protected abstract void populateCorrelationCounts(ProbabilityBundle bundle);
}
