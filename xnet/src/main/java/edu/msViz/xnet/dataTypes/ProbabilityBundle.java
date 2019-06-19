/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import edu.msViz.xnet.TraceClusterer;
import edu.msViz.xnet.Utility;
import java.security.InvalidParameterException;
import java.util.Arrays;

/**
 * Bundle of variable outcomes and there probabilities (some conditional) 
 * for Isotope Trace clustering variables
 * @author kyle
 */
public class ProbabilityBundle 
{
    /**
     * Configurations for probability tables.
     * Describes the outcomes in each probability table.
     */
    public static class Config
    {
        public static final Boolean[] ASSOCIATION_OUTCOMES = new Boolean[]{false, true};
        
        protected static final float SEPARATION_OUTCOME_START = 0.0f;
        protected static final float SEPARATION_OUTCOME_STEP = .001f;
        protected static final float SEPARATION_OUTCOME_END = TraceClusterer.MAX_SEPARATION;
        
        // inclusive range
        protected static final int NUM_SEPARATION_OUTCOMES = 
                Math.round((Config.SEPARATION_OUTCOME_END - Config.SEPARATION_OUTCOME_START) / Config.SEPARATION_OUTCOME_STEP + 1);
        
        protected static final float CORRELATION_OUTCOME_START = -1.0f;
        protected static final float CORRELATION_OUTCOME_STEP = .001f;
        protected static final float CORRELATION_OUTCOME_END = 1.0F;
        
        // inclusive range
        protected static final int NUM_CORRELATION_OUTCOMES = 
                Math.round((Config.CORRELATION_OUTCOME_END - Config.CORRELATION_OUTCOME_START) / Config.CORRELATION_OUTCOME_STEP + 1);
    }
    
    /**
     * Random variables 
     */
    public static enum RV {ASSOCIATION, SEPARATION, CORRELATION};
    
    /**
     * Occurrence counts of corresponding association outcomes
     */
    private final int[] associationCounts;
    
    /**
     * Occurrence counts of corresponding separation outcomes
     */
    private final int[] separationCounts;
    
    /**
     * Occurrence counts of corresponding correlation outcomes
     */
    private final int[] correlationCounts;
    
    /**
     * Array of separation outcomes, in ascending order
     */
    public final Float[] separationOutcomes;
    
    /**
     * Array of correlation outcomes, in ascending order
     */
    public final Float[] correlationOutcomes;
    
    /**
     * Default constructor. Initializes count arrays according to configuration
     * @param startingCount initial count for all correlation and separation bins
     */
    public ProbabilityBundle(int startingCount)
    {
        
        // bin per association outcome
        this.associationCounts = new int[Config.ASSOCIATION_OUTCOMES.length];
        
        // separation outcomes
        this.separationOutcomes = new Float[Math.round((Config.SEPARATION_OUTCOME_END - Config.SEPARATION_OUTCOME_START) / Config.SEPARATION_OUTCOME_STEP + 1)];
        for(int i = 0; i < this.separationOutcomes.length; i++)
            this.separationOutcomes[i] = Utility.fRound(i * Config.SEPARATION_OUTCOME_STEP);
        
        // bin per assocation outcome per separation outcome
        this.separationCounts = new int[this.separationOutcomes.length * Config.ASSOCIATION_OUTCOMES.length];
        
        // initialize all separation counts to starting count
        Arrays.fill(this.separationCounts, startingCount);
        
        this.correlationOutcomes = new Float[Math.round((Config.CORRELATION_OUTCOME_END - Config.CORRELATION_OUTCOME_START) / Config.CORRELATION_OUTCOME_STEP + 1)];
        for(int i = 0; i < this.correlationOutcomes.length; i++)
            this.correlationOutcomes[i] = Utility.fRound((i * Config.CORRELATION_OUTCOME_STEP) + Config.CORRELATION_OUTCOME_START);
        
        // bin per assocation outcome per correlation outcome
        this.correlationCounts = new int[this.correlationOutcomes.length * Config.ASSOCIATION_OUTCOMES.length];
        
        // initialize all correlation counts to starting count
        Arrays.fill(this.correlationCounts, startingCount);
        
    }
    
    /**
     * Adds an occurrence of an association or non-association to the counts within
     * the probability bundle
     * @param association boolean association value
     * @param separation double separation value
     * @param correlation double correlation values
     */
    public void addOccurrence(boolean association, double separation, double correlation)
    {
        this.incrementBin(RV.ASSOCIATION, association, null, 1);
        this.incrementBin(RV.SEPARATION, (float)separation, association, 1);
        this.incrementBin(RV.CORRELATION, (float)correlation, association, 1);
    }
    
    
    /**
     * Increments the bin of the RV's bin corresponding to the outcome
     * @param rv variable whose count array is to modified
     * @param outcome outcome whose bin is to be incremented
     * @param parentOutcome outcome of parent variable (if applicable)
     * @param increment amount to increment bin
     */
    public void incrementBin(RV rv, Object outcome, Object parentOutcome, int increment)
    {
        int binNumber = this.getBinNumber(rv, outcome, parentOutcome);
        switch(rv)
        {
            case ASSOCIATION:
                this.associationCounts[binNumber] += increment;
                break;
            case SEPARATION:
                this.separationCounts[binNumber] += increment;
                break;
            case CORRELATION:
                this.correlationCounts[binNumber] += increment;
                break;
        }
    }
    
    
    /**
     * Sets the count of an an RV's outcome
     * @param rv variable whose count array is to be modified
     * @param binNumber index of the bin to set
     * @param parentOutcome outcome of parent variable (if applicable)
     * @param count value to set
     */ 
    public void setCount(RV rv, int binNumber, Object parentOutcome, int count)
    {
        switch(rv)
        {
            case ASSOCIATION:
                this.associationCounts[binNumber] = count;
                break;
            case SEPARATION:
                int rowOffset = ((Boolean)parentOutcome ? 1 : 0) * Config.NUM_SEPARATION_OUTCOMES;
                this.separationCounts[rowOffset + binNumber] = count;
                break;
            case CORRELATION:
                int _rowOffset = ((Boolean)parentOutcome ? 1 : 0) * Config.NUM_CORRELATION_OUTCOMES;
                this.correlationCounts[_rowOffset + binNumber] = count;
                break;
        }
    }
    
    /**
     * Get's the bin number of the outcome for the given variable
     * @param rv random variable whose array is to be assessed
     * @param outcome outcome of RV whose bin should be discovered
     * @param parentOutcome outcome of parent (if applicable)
     * @return bin number of outcome in RV's count array
     */
    private int getBinNumber(RV rv, Object outcome, Object parentOutcome)
    {
        
        switch(rv)
        {
            case ASSOCIATION:
                boolean outcomeBoolean = (Boolean)outcome;
                return outcomeBoolean ? 1 : 0;
            case SEPARATION:
                int rowOffset = (Boolean)parentOutcome ? 1 : 0;
                float sOutcome = (Float)outcome;
                int binNumber = rowOffset * this.separationOutcomes.length 
                        + Utility.getNearestIndex(this.separationOutcomes, sOutcome);
                return binNumber;
            case CORRELATION:
                int _rowOffset = (Boolean)parentOutcome ? 1 : 0;
                float cOutcome = (Float)outcome;
                int _binNumber = _rowOffset * this.correlationOutcomes.length + 
                        Utility.getNearestIndex(this.correlationOutcomes, cOutcome);
                return  _binNumber;
            default:
                throw new InvalidParameterException("Invalid random variable selection.");
        }
    }
    
    /**
     * Returns the probabilities of a random variable
     * @param rv random variable enumeration
     * @return double array of probabilities
     */
    public double[] getProbabilities(RV rv)
    {        
        // sum of all occurances in count array (double for division purposes)
        double total;
        
        // counts array to operate on
        int[] counts;
        
        // definitely the worst part of this function...
        switch(rv)
        {
            case ASSOCIATION:
                counts = this.associationCounts;
                break;
            case SEPARATION:
                counts = this.separationCounts;
                break;
            case CORRELATION:
                counts = this.correlationCounts;
                break;
            default:
                throw new InvalidParameterException("Invalid random variable selection.");
        }
        
        // sum counts for total
        total = Arrays.stream(counts).sum();
        
        // normalize counts = probabilities
        return Arrays.stream(counts).mapToDouble(count -> count / total).toArray();
    }
    
    /**
     * Gets the count of the chosen variable given the outcome and parent's outcome
     * @param rv chosen random variable
     * @param outcome rv's outcome
     * @param parentOutcome rv parent's outcome (if applicable)
     * @return count of the variable given the outcomes
     */
    public int getCount(RV rv, Object outcome, Object parentOutcome)
    {
        // bin number corresponding to outcome
        int binNumber = this.getBinNumber(rv, outcome, parentOutcome);
        
        switch(rv)
        {
            case ASSOCIATION:
                return this.associationCounts[binNumber];
            case SEPARATION:
                return this.separationCounts[binNumber];
            case CORRELATION:
                return this.correlationCounts[binNumber];
            default:
                throw new InvalidParameterException("Invalid random variable selection.");
        }
    }
    
    /**
     * Absorbs the counts of another probability bundle into this probability bundle
     * @param otherBundle bundle whose counts are to be absorbed
     */
    public void absorb(ProbabilityBundle otherBundle)
    {
        for(boolean assocOutcome : Config.ASSOCIATION_OUTCOMES)
        {
            // increment association outcome by other bundle's corresponding outcome
            this.incrementBin(RV.ASSOCIATION, assocOutcome, null, otherBundle.getCount(RV.ASSOCIATION, assocOutcome, null));
            
            // increment each separation count by other bundle
            Arrays.stream(this.separationOutcomes).forEach(outcome -> {
                this.incrementBin(RV.SEPARATION, outcome, assocOutcome, otherBundle.getCount(RV.SEPARATION, outcome, assocOutcome));
            }); 
            
            // increment each correlation count by other bundle
            Arrays.stream(this.correlationOutcomes).forEach(outcome -> {
                this.incrementBin(RV.CORRELATION, outcome, assocOutcome, otherBundle.getCount(RV.CORRELATION, outcome, assocOutcome));
            });
        }
    }
}
