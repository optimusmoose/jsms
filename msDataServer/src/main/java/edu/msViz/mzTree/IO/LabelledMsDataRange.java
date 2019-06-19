/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.IO;

/**
 * MsDataRange with a label
 * @author kyle
 */
public class LabelledMsDataRange extends MsDataRange{
    
    /**
     * As promised, a label
     */
    public String label;
    
    /**
     * Default constructor, accepting all fields
     * @param label range's label
     * @param mzMin range's minimum mz value
     * @param mzMax range's maximum mz value
     * @param rtMin range's minimum rt value
     * @param rtMax range's maximum rt value
     */
    public LabelledMsDataRange(String label, double mzMin, double mzMax, float rtMin, float rtMax) 
    {
        super(mzMin, mzMax, rtMin, rtMax);
        this.label = label;
    }
    
}
