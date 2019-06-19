/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.harnesses;

import edu.msViz.mzTree.MzTree;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory;
import java.io.File;
import java.io.PrintWriter;

/**
 * Performs numIterations conversions on each file in filenames, recording conversion
 * times in the output file
 * @author kyle
 */
public class MzTreeConversion {
    public static void main(String[] args)
    {        
        // parameters
        String relPath = "../../data/mzml/";
        File output = new File("../comparison/results/conversion_results.txt");
        
        String[] filenames = 
        /*{"CHPP_SDS_3002.mzML",
        "GRAIN_DEVELOPMENT_Z71_3.mzML",
        "STEM_12.mzML",
        "18185_REP2_4pmol_UPS2_IDA_1.mzML",
        "POLLEN_2",
        "Sheppard_Werner_RNAPORF145_09.mzML",*/
        {"Sheppard_Werner_RNAPORF145_06.mzML",
        "Sheppard_Werner_RNAPORF145_03.mzML"};
        
        int numIterations = 2;
        
        try (PrintWriter writer = new PrintWriter(output.getAbsolutePath())) {
            for(String file : filenames){
                                
                writer.println(file);
                
                // accumulate execution time
                long totalExecTime = 0; // in millis for now
                for(int i = 0; i < numIterations; i++)
                {
                    MzTree mzTree = new MzTree();
                    long start = System.currentTimeMillis();
                    mzTree.load(relPath+file, SummarizationStrategyFactory.Strategy.WeightedStriding);
                    long end = System.currentTimeMillis();
                    
                    File mzTreeFile = new File(mzTree.getImportState().getMzTreeFilePath());
                    File mzTreePointsFile = new File(mzTree.getImportState().getMzTreeFilePath()+"-points");
                    File mzTreeIntensityFile = new File(mzTree.getImportState().getMzTreeFilePath()+"-intensity");
                    
                    if(i==0)
                    {
                        writer.println(mzTreeFile.getName() + " bytes: " + mzTreeFile.length());
                        writer.println(mzTreePointsFile.getName() + " bytes: " + mzTreePointsFile.length());
                        if(mzTreeIntensityFile.exists())
                            writer.println(mzTreeIntensityFile.getName() + " bytes: " + mzTreePointsFile.length());
                    }
                    
                    mzTree.close();
                    mzTreeFile.delete();
                    mzTreePointsFile.delete();
                    
                    if(mzTreeIntensityFile.exists())
                        mzTreeIntensityFile.delete();
                    
                    long thisTrialExecTime = end - start;
                    writer.println("trial " + i + ": " + (thisTrialExecTime / (double)1000));
                    writer.flush();
                    
                    totalExecTime += thisTrialExecTime;
                    
                }
                
                // output average execution time in seconds
                double avgExecTimeMillis = (double)totalExecTime / (double)numIterations;
                double avgExecTimeSeconds = avgExecTimeMillis / (double)1000;
                writer.println("Average execution time (s): " + avgExecTimeSeconds);
                writer.flush();
            }
        }
        catch(Exception ex){
            System.err.println(ex.getMessage());
        }
    }
}
