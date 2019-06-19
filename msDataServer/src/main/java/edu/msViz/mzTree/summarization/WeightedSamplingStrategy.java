/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Summarization strategy that selects points using weighted random sampling
 * @author Kyle
 */
public class WeightedSamplingStrategy extends SummarizationStrategy
{
    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints)
    {
        // sum of intensity values
        float intensitySum = (float)this.sumIntensity(dataset);
        
        // f
        float f = numPoints / intensitySum;
        
        // arraylist to store selected datapoints
        List<MsDataPoint> selection = new ArrayList<>(numPoints);
        Random random = new Random(System.currentTimeMillis());

        // test each point for inclusion
        for(int i = 0; selection.size() < numPoints; i = (i+1) % dataset.size())
        {
            float p = f * (float)dataset.get(i).intensity;
            if(random.nextFloat() <= p/2)
                selection.add(dataset.get(i));
        }
        
        return selection;
    }
    
}
