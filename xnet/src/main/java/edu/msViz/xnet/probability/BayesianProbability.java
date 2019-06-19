/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.probability;
import edu.msViz.xnet.TraceClusterer;
import edu.msViz.xnet.dataTypes.ProbabilityBundle;
import org.apache.commons.math3.distribution.NormalDistribution;
import edu.msViz.xnet.dataTypes.ProbabilityBundle.RV;
import java.util.ArrayList;

/**
 * Uses bayesian reasoning (prior knowledge) to populate probability tables
 * Prior knowledge based on latent properties of isotopic envelopes
 * @author kyle
 */
public class BayesianProbability extends ProbabilityAggregator
{
    /**
     * Probability of being associated is recognizably small, 
     * however a theoretical value is not readily derived. It depends
     * on the ratio of envelopes to traces in the dataset. Assume 10%
     */
    private static final float ASSOC_PROB = .1f;
    
    /**
     * height (difference from baseline) of maximum probability outcomes 
     */ 
    private static final int PEAK_HEIGHT = 10000;
    
    /**
     * starting count for all sep/corr outcomes (to avoid probabilities of 0)
     */ 
    private static final int BASELINE = 100;
    
    /**
     * slope of rectified unit used for theoretical correlation probabilities
     */ 
    private static final float CORRELATION_SLOPE = 1f;

    /**
     * standard deviation to be uniformly applied to all separation distributions
     */
    private static final float UNIFORM_STDEV = .01f;
    
    /** 
     * scaled normal distribution functions
     */ 
    private final ArrayList<SeparationDistribution> separationDistributions;
        
    /**
     * Default constructor, creates the separation distribution functions
     */
    public BayesianProbability()
    {
        this.separationDistributions = new ArrayList<>();
        for(int charge = 1; charge <= TraceClusterer.MAX_CHARGE; charge++)
        {
            this.separationDistributions.add(new SeparationDistribution(charge, PEAK_HEIGHT, UNIFORM_STDEV));
        }
    }
    
    @Override
    protected ProbabilityBundle initBundle()
    {
        ProbabilityBundle bundle = new ProbabilityBundle(BASELINE);
        return bundle;
    }
    
    @Override
    protected void populateAssociationCounts(ProbabilityBundle bundle)
    {
        // association = false
        bundle.incrementBin(RV.ASSOCIATION, false, null, (int)((1f - ASSOC_PROB) * PEAK_HEIGHT));
        
        // association = true
        bundle.incrementBin(RV.ASSOCIATION, true, null, (int)(ASSOC_PROB * PEAK_HEIGHT));
    }
    
    @Override
    protected void populateSeparationCounts(ProbabilityBundle bundle)
    {        
        // iterate through each count bin
        for(float outcome : bundle.separationOutcomes)
        {
            // sum from all separation distributions for outcome
            double sum = this.separationDistributions.stream().mapToDouble((distro) -> distro.density(outcome)).sum();
            
            // convert to integer count, store in bundle for association = true
            int count = (int)sum;
            bundle.incrementBin(RV.SEPARATION, outcome, true, count);
            
            // compute complement, store in bundle for association = false
            int complement = PEAK_HEIGHT - count;
            bundle.incrementBin(RV.SEPARATION, outcome, false, complement);
        }
    }
            
    @Override
    protected void populateCorrelationCounts(ProbabilityBundle bundle)
    {        
        float correlationPeak = PEAK_HEIGHT * CORRELATION_SLOPE;
        for(float outcome : bundle.correlationOutcomes)
        {
            //int count = (int)(correlationPeak * this.relu(outcome));
            int count = (int)(correlationPeak * this.relu(outcome));
            
            //association = false
            bundle.incrementBin(RV.CORRELATION, outcome, false, (int)correlationPeak - count);
            
            // association = true
            bundle.incrementBin(RV.CORRELATION, outcome, true, count);
        }
    }
    
    /**
     * Rectified linear unit function. 
     * @param x input float
     * @return x if x greater than 0, else 0
     */
    private float relu(float x)
    {
        return x > 0 ? x : 0;
    }
    
    /**
     * Desired probability distribution for the separation set by a charge state
     * on the aggregate separation distribution for association = true
     */
    private class SeparationDistribution
    {
        /**
         * unscaled normal distribution
         */ 
        private final NormalDistribution normDist;
        
        /**
         * scale to apply to pdf(x) on encapsulated distribution
         */ 
        private final float scale;
        
        /**
         * Default constructor. Constructs a normal distribution with mean = 1 / chargeState
         * scaled so that pdf(1/chargeState) = peakHeight
         * @param chargeState charge state that the distribution represents
         * @param peakHeight desired value of pdf(mean)
         * @param stdev desired standard deviation of normal distribution
         */
        public SeparationDistribution(int chargeState, float peakHeight, float stdev)
        {
            // normal distribution's mean
            float myMean = 1.0f / chargeState;
            
            // create normal distribution
            this.normDist = new NormalDistribution(myMean, stdev);
            
            // each distribution should be scaled to have pdf(mean) reach peakHeight
            // simply save the scale and apply to each value retrieved from the distribution
            // to ascertain scale, get pdf(mean) from normal distribution with desired stdev,
            // divide peak height by pdf(mean)
            this.scale = peakHeight / (float)this.normDist.density(this.normDist.getMean());
        }
        
        /**
         * Retrieves the density of the distribution at x, scaled
         * @param x input
         * @return density at x, scaled
         */
        public float density(float x)
        {
            return this.scale * (float)this.normDist.density(x);
        }
    }
}
