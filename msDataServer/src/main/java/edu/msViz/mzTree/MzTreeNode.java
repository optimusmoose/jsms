/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree;

import edu.msViz.mzTree.summarization.SummarizationStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * Node class for MzTree
 *
 * @author rob
 */
public class MzTreeNode {
    
    //Node's ID
    public int nodeID;
    
    // absolute index of node in point file
    public Long fileIndex;
    
    // number of points stored in point file (leaf nodes only)
    public Integer numSavedPoints;
    
    //Child nodes
    public ArrayList<MzTreeNode> children;
    
    // IDs of the MsDataPoints belonging to this node
    public int[] pointIDs;
    
    //minimum mz at this node and below
    public double mzMin;
    
    //maximum mz at this node and below
    public double mzMax;
    
    //minimum rt at this node and below
    public float rtMin;
    
    //maximum rt at this node and below
    public float rtMax;
    
    // minimum int at this node and below
    public double intMin;
    
    // maximum int at this node and below
    public double intMax;
    
    /**
     * Constructor that doesn't require the number of children expected
     */
    public MzTreeNode()
    {
        // init to respective extremes
        this.rtMin = Float.MAX_VALUE;
        this.mzMin = Double.MAX_VALUE;
        this.intMin = Double.MAX_VALUE;
        this.rtMax = Float.MIN_VALUE;
        this.mzMax = Double.MIN_VALUE;
        this.intMax = Double.MIN_VALUE;
        
        this.children = new ArrayList<>();
    }
    
    /**
     * Default constructor
     * @param n number of children to store
     */
    public MzTreeNode(int n) {
        
        // init to respective extremes
        this.rtMin = Float.MAX_VALUE;
        this.mzMin = Double.MAX_VALUE;
        this.intMin = Double.MAX_VALUE;
        this.rtMax = Float.MIN_VALUE;
        this.mzMax = Double.MIN_VALUE;
        this.intMax = Double.MIN_VALUE;

        // leaves will not have children
        // but root/hidden nodes will
        this.children = new ArrayList<>(n);
    }

    /**
     * collects a child node and keeps min/max mz/rt/int
     * @param child child to reference and absorb mins/maxes
     */
    public void addChildGetBounds(MzTreeNode child) 
    {
        this.children.add(child);
        
        // compare mins/maxs and keep extremes
        this.keepLargestMzMax(child.mzMax);
        this.keepSmallestMzMin(child.mzMin);
        this.keepLargestRtMax(child.rtMax);
        this.keepSmallestRtMin(child.rtMin);
        this.keepLargestIntMax(child.intMax);
        this.keepSmallestIntMin(child.intMin);
    }

    /**
     * Collects pointIDs of a dataset and discovers min/max mz/rt/int
     * @param msData dataset to process
     */
    public void initLeaf(List<MsDataPoint> msData) {
        
        // set pointIDs array
        this.collectPointIDs(msData);
        
        // discover the minimums and maximums for mz,rt,int
        for (int i = 0; i < msData.size(); i++) {
            MsDataPoint curPoint = msData.get(i);
            
            // keep largest maxes, smallest mins
            // mz
            mzMax = (curPoint.mz > mzMax) ? curPoint.mz : mzMax;
            mzMin = (curPoint.mz < mzMin) ? curPoint.mz : mzMin;
            // rt
            rtMax = (curPoint.rt > rtMax) ? (float)curPoint.rt : rtMax;
            rtMin = (curPoint.rt < rtMin) ? (float)curPoint.rt : rtMin;
            // int
            intMax = (curPoint.intensity > intMax) ? curPoint.intensity : intMax;
            intMin = (curPoint.intensity < intMin) ? curPoint.intensity : intMin;
        }
    }
    
    /**
     * Collects the pointIDs from the given points into pointIDs member
     * @param points dataset to process
     */
    public void collectPointIDs(List<MsDataPoint> points){
        this.pointIDs = points.stream().mapToInt(p -> p.pointID).toArray();
    }
    
    /**
     * Collects a summary from the set of all childrens' data points
     * @param numPoints number of points to collect
     * @param summarizer summarization strategy to gather sample with
     * @param dataStorage
     */
    public void summarizeFromChildren(int numPoints, SummarizationStrategy summarizer, PointCache pointCache)
    {
        List<MsDataPoint> childrensPoints = new ArrayList<>();
        
        
        // collect all childrens' MsDataPoints from cache
        for(MzTreeNode childNode : this.children) {
            childrensPoints.addAll(pointCache.retrievePoints(childNode.pointIDs));
        }
        
        // summarize and collect point IDs
        this.collectPointIDs(summarizer.summarize(childrensPoints, numPoints));
        
    }
    
    /**
     * compare and keep min/max for each of mz, rt and intensity
     */
    
    public void keepLargestMzMax(double _mzMax) {
        if (_mzMax > mzMax) mzMax = _mzMax;
    }

    public void keepSmallestMzMin(double _mzMin) {
        if (_mzMin < mzMin) mzMin = _mzMin;
    }

    public void keepLargestRtMax(float _rtMax) {
        if (_rtMax > rtMax) rtMax = _rtMax;
    }

    public void keepSmallestRtMin(float _rtMin) {
        if (_rtMin < rtMin) rtMin = _rtMin;
    }
    
    public void keepLargestIntMax(double _intMax) {
        if (_intMax > intMax) intMax = _intMax;
    }

    public void keepSmallestIntMin(double _intMin) {
        if (_intMin < intMin) intMin = _intMin;
    }
}
