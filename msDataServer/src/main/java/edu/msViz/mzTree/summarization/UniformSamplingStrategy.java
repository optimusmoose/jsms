/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.List;

/**
 * Summarization strategy that selects using uniform random sampling
 * @author Kyle
 */
public class UniformSamplingStrategy extends SummarizationStrategy
{
    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints)
    {
        // shuffle the dataset (enter stochasticity)
        this.shuffle(dataset);
                
        // return first numPoints
        return dataset.subList(0, numPoints);
        
    }   
}
