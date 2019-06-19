/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import edu.msViz.xnet.probability.BayesianProbability;
import org.junit.Test;
import org.junit.Assert;
import edu.msViz.xnet.dataTypes.ProbabilityBundle;
import edu.msViz.xnet.dataTypes.ProbabilityBundle.RV;

/**
 *
 * @author kyle
 */
public class ProbabilityAggregatorTest 
{
    ProbabilityBundle bayesBundle;
    
    private static final double DELTA = 1e-15;
    
    public ProbabilityAggregatorTest()
    {
        BayesianProbability bayesProbs = new BayesianProbability();
        bayesBundle = bayesProbs.getProbabilities();
    }
    
    @Test
    public void BayesianAssocTest()
    {
        double [] assocProbs = bayesBundle.getProbabilities(RV.ASSOCIATION);
        
        // assert that prob(assoc=false) > prob(assoc=true)
        Assert.assertTrue(assocProbs[0] > assocProbs[1]);
    }
    
    @Test
    public void BayesSepTest()
    {
        // separation probabilities
        double [] sepProbs = bayesBundle.getProbabilities(RV.SEPARATION);
        
        // separation outcomes (half the length of separation probabilities)
        Float[] sepOutcomes = bayesBundle.separationOutcomes;
        
        // outcomes corresponding to charge state separations
        Float[] chargeSeparationOutcomes = new Float[TraceClusterer.MAX_CHARGE];
        for(int c = 1; c <= TraceClusterer.MAX_CHARGE; c++) 
            chargeSeparationOutcomes[c-1] = sepOutcomes[Utility.getNearestIndex(sepOutcomes, 1/(float)c)];
        
        // assert wavelike pattern, with maxima at charge state separations.
        // targets only second half of probability array (association = true)
        int sepProbsOffset = sepProbs.length / 2;
        for(int i = 0; i < sepOutcomes.length; i++)
        {
            // current outcome and corresponding probability
            double curProb = sepProbs[sepProbsOffset + i];
            double curOutcome = sepOutcomes[i];
            
            // get probability of nearest charge separation outcome
            double nearestChargeSeparationOutcome = chargeSeparationOutcomes[Utility.getNearestIndex(chargeSeparationOutcomes, curOutcome)];
            int nearestChargeSeparationIndex = Utility.getNearestIndex(sepOutcomes, nearestChargeSeparationOutcome);
            double nearestChargeSeparationProb = sepProbs[sepProbsOffset + nearestChargeSeparationIndex];
            
            // if this outcome is a charge separation outcome
            // assert indexes are equal
            if(curOutcome == nearestChargeSeparationOutcome)
                Assert.assertEquals(i, nearestChargeSeparationIndex);
            
            // else assert that nearest charge separation is local maxima
            else
                Assert.assertTrue(curProb < nearestChargeSeparationProb);
        }
    }
    
    @Test
    public void BayesCorrTest()
    {
        double[] corrProbs = bayesBundle.getProbabilities(RV.CORRELATION);
        Float[] corrOutcomes = bayesBundle.correlationOutcomes;
        
        int corrProbsOffset = corrProbs.length / 2;
        
        // probability of all outcomes <- 0
        double baselineProb = corrProbs[corrProbsOffset];
        double prevProb = baselineProb;
        // assert rectified linear unit curve
        // targets only second half of probability array (association = true)
        for(int i = 0; i < corrOutcomes.length; i++)
        {
            // current prob and outcome
            double curProb = corrProbs[corrProbsOffset + i];
            double curOutcome = corrOutcomes[i];
            
            // all outcome less than equal to zero have prob of 0
            if(curOutcome <= 0)
                Assert.assertEquals(baselineProb, curProb, DELTA);
            
            // else should have an increasing trend
            else
                Assert.assertTrue(prevProb < curProb);
            
            prevProb = curProb;
        }
    }
}
