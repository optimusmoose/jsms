/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.harnesses;

import edu.msViz.mzTree.IO.MzmlParser;

/**
 *
 * @author kyle
 */
public class MzmlPointCounter 
{
    public static void main(String args[])
    {
                
        // parameters
        String relPath = "../../data/mzml/";
        
        String[] filenames = {"D1_Control4_TechRep_1.mzML",
            "Singer_BGWC_8plex_04_9May13_Methow_13-02-13.mzML",
            "PaMA150528_J4-3_Q1468.mzML",
            "140924_12.mzML",
            "140924_11.mzML",
            "PaMA150528_J8-3_Q1262_150713163831.mzML",
            "PaMA150528_J7-2_Q1475.mzML"};
        
        
        for(String file : filenames){

            try {
                MzmlParser parser = new MzmlParser(relPath + file);
                int numPoints = parser.countPoints();
                System.out.println(file + ": " + numPoints);
            } catch (Exception ex) {
                System.err.println("Failed on: " + file + " || " + ex.getMessage());
            }

            
        }
        
    }
}
