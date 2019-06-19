/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.harnesses;

/**
 *
 * @author kyle
 */
public class ConversionResults
    {
        public long executionMS;
        public long fileSizeBytes;
        public long numPoints;
        
        public ConversionResults(long execMS, long fSize, long nPts)
        {
            this.executionMS = execMS;
            this.fileSizeBytes = fSize;
            this.numPoints = nPts;
        }
    }
