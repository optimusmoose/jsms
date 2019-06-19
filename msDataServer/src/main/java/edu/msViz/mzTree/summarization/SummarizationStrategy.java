/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.List;
import java.util.Random;

/**
 * Summarization strategy base class
 * Heirs must implement abstract method "summarize"
 * @author Kyle
 */
public abstract class SummarizationStrategy
{
    /**
     * Summarize the given dataset by selecting numPoints of the set
     * @param dataset dataset to summarize
     * @param numPoints number of points to select
     * @return summarized dataset
     */
    public abstract List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints);
    
    /**
     * Fisher-Yates shuffle algorithm
     * @param dataset 
     */
    protected void shuffle(List<MsDataPoint> dataset)
    {
        // random number generator
        Random rnd = new Random(System.currentTimeMillis());
        
        // shuffle the data, starting at last point swap with random point
        for (int i = dataset.size() - 1; i > 0; i--)
        {
            // index of point to swap with (always precedes focused point)
            int index = rnd.nextInt(i + 1);
            
            // swap
            MsDataPoint temp = dataset.get(index);
            dataset.set(index, dataset.get(i));
            dataset.set(i, temp);
        }
    }
    
    protected double sumMz(List<MsDataPoint> dataset){
        double accumulator = 0;
        for(MsDataPoint p : dataset)
            accumulator += p.mz;
        return accumulator;
    }
    
    protected float sumRt(List<MsDataPoint> dataset){
        float accumulator = 0;
        for(MsDataPoint p : dataset)
            accumulator += p.rt;
        return accumulator;
    }
    
    protected double sumIntensity(List<MsDataPoint> dataset){
        double accumulator = 0;
        for(MsDataPoint p : dataset)
            accumulator += p.intensity;
        return accumulator;
    }
}
