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
 * Summarization strategy that accumulates the intensities of an intensity sorted
 * MS dataset, collecting points that send the accumulation over a threshold
 * @author Kyle
 */
public class WeightedStridingStrategy extends SummarizationStrategy
{
    // length that stride jumps between array elements
    final int STRIDE_LENGTH = 43;

    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints)
    {
        if (dataset.size() <= numPoints) {
            return new ArrayList<>(dataset);
        }

        List<MsDataPoint> skippedPoints = new ArrayList<>();
        ArrayList<MsDataPoint> selection = new ArrayList<>(numPoints);

        // running total of accumulated intensity since the last selected point
        double intensityAccumulation = 0;
        
        // Threshold used during accumulation to decide when a point is included.
        // Total intensity divided by desired number of points gets a nice looking sample
        double accumulationThreshold = this.sumIntensity(dataset) / numPoints;

        int i = 0;
        while (selection.size() < numPoints) {
            MsDataPoint point = dataset.get(i);
            intensityAccumulation += point.intensity;

            // when accumulation passes the threshold, select the point and reduce the accumulator
            if (intensityAccumulation >= (accumulationThreshold - 1.0e-5)) {
                selection.add(point);
                intensityAccumulation -= accumulationThreshold;
            } else {
                skippedPoints.add(point);
            }
            
            i += STRIDE_LENGTH;

            if (i >= dataset.size()) {
                // stride through data again, starting with a new index
                i = (i+1) % STRIDE_LENGTH;
            }

            if (i == 0) {
                // every point in dataset has been examined, so now only consider the previously skipped points
                // this prevents already added points from being added again
                dataset = skippedPoints;
                skippedPoints = new ArrayList<>();
            }
        }
        
        return selection;
    }
}
