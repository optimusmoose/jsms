/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.summarization;

import edu.msViz.mzTree.MsDataPoint;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 *
 * @author André
 * see Algorithm A-Res at: https://en.wikipedia.org/wiki/Reservoir_sampling#Weighted_Random_Sampling_using_Reservoir
 * 
 */
public class WeightedReservoirSampling extends SummarizationStrategy
{
    @Override
    public List<MsDataPoint> summarize(List<MsDataPoint> dataset, int numPoints)
    {
        Random rand = new Random();
        // PriorityQueue for holding numPoints points and removing lowest priority point when replaced
        PriorityQueue<WeightedPoint> q = new PriorityQueue<>();
        List<MsDataPoint> pointsToReturn = new ArrayList<>(numPoints);
        
        // Looks at every point in data set and randomly replaces a point
        // with a new point when appropriate based on weights
        for (int i = 0; i < dataset.size(); i++) {
            WeightedPoint p = new WeightedPoint();
            p.point = dataset.get(i);
            p.priority = Math.pow(rand.nextDouble(),(1.0/p.point.intensity)); // priority calculated based on random double and weight
            if (q.size() < numPoints) {  //add every point until we have numPoints
                q.add(p);
            }
            else if (q.peek().priority < p.priority) {  //if new point has higher priority than lowest priority point in queue, replace
                q.poll();
                q.add(p);
            }
        }
        
        // iterates over PriorityQueue and adds each MsDataPoint to an array
        Iterator<WeightedPoint> it = q.iterator();
        for (int i = 0; it.hasNext(); i++) {
            pointsToReturn.set(i,it.next().point);
        }
        
        
        return pointsToReturn;
    }
    
}
// WeightedPoint class because points need to have a stored priority to compare
class WeightedPoint implements Comparable<WeightedPoint>{
    MsDataPoint point;
    double priority;

    @Override
    public int compareTo(WeightedPoint other) {
        return (Double.compare(this.priority, other.priority));
    }
}

