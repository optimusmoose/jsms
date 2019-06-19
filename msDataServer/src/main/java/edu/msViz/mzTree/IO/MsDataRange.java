/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.IO;

/**
 * 2D data range in the m/z*RT plane
 * @author kyle
 */
public class MsDataRange
{
    // minimum/maximum bounds on m/z and rt axes
    public double mzMin;
    public double mzMax;
    public float rtMin;
    public float rtMax;

    /**
     * Default constructor, accepting all fields
     * @param mzMin range's minimum mz value
     * @param mzMax range's maximum mz value
     * @param rtMin range's minimum rt value
     * @param rtMax range's maximum rt value
     */
    public MsDataRange(double mzMin, double mzMax, float rtMin, float rtMax)
    {
        this.mzMin = mzMin;
        this.mzMax = mzMax;
        this.rtMin = rtMin;
        this.rtMax = rtMax;
    }

    /**
     * copy constructor
     * @param otherRange
     */
    public MsDataRange(MsDataRange otherRange)
    {
        this(otherRange.mzMin,otherRange.mzMax,otherRange.rtMin,otherRange.rtMax);
    }
}
