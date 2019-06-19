/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Summarization strategy that selects data points by striding through the dataset
 * @author Kyle
 */
public class UniformStridingStrategy extends SummarizationStrategy
{
    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints)
    {
        // calculate length of the stride that will achieve the desired
        // number of points
        int strideLength = dataset.size()/numPoints;
        
        // the selected datapoints
        List<MsDataPoint> selection = new ArrayList<>(numPoints);
        
        // stride through dataset and collect sample
        for(int i = 0; i < numPoints; i++)
            selection.set(i, dataset.get(i * strideLength));
        
        return selection;
    }
}
