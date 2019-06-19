/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Summarization strategy that selects the numPoints most intense points
 * @author Kyle
 */
public class IntensityCutoffStrategy extends SummarizationStrategy
{
    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints){
        
        // sort dataset by intensity
        Collections.sort(dataset, Comparator.comparing((MsDataPoint dataPoint) -> dataPoint.intensity));
        
        // return final numPoints of sorted array (numPoints most intense points)
        return dataset.subList(dataset.size() - numPoints,dataset.size());
    }
}
