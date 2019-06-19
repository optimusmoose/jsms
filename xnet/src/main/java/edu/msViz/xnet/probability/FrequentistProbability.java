/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.probability;

import edu.msViz.xnet.dataTypes.ProbabilityBundle;
import edu.msViz.xnet.dataTypes.IsotopeTrace;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import com.google.gson.Gson;
import edu.msViz.xnet.dataTypes.TraceGrid;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.TreeSet;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

/**
 * Probability model based on frequentist statistics (data derived)
 * @author kyle
 */
public class FrequentistProbability extends ProbabilityAggregator
{
    /**
     * starting count for each RV outcome
     */ 
    private static int STARTING_COUNT = 1;
    
    /**
     * Probability file containing previously derived probabilities.
     * Probabilities contained are used to populate outcome probabilities
     */
    private File probabilityFile;
    
    /**
     * no argument constructor: use default probability file found in resources folder
     */ 
    public FrequentistProbability()
    {
        ClassLoader classLoader = getClass().getClassLoader();
        this.probabilityFile = new File(classLoader.getResource("frequentist.json").getFile());
    }

    /**
     * Frequentist probability accepting user specified probability file
     * @param inputFilePath user specified probability file
     */
    public FrequentistProbability(String inputFilePath)
    {
        this.probabilityFile = new File(inputFilePath);
    }
        
    /**
     * constructor accepting a set of traces from which to populate probability bundle
     * this method should not be used in trace clustering, solely in creating/modifying probability files
     * @param traces input traces
     * @param filePath location of probability file. Increments counts if file exists, otherwise creates new file
     * @throws java.io.IOException
     */
    public FrequentistProbability(List<IsotopeTrace> traces, String filePath) throws IOException
    {
        // probability file location (may or may not exist)
        File probFile = new File(filePath);
  
        // if file exists init prob bundle from file, else new bundle
        ProbabilityBundle bundle = new File(filePath).exists() ? this.fileToBundle(probFile) : new ProbabilityBundle(STARTING_COUNT);
        
        this.train(traces, bundle);
        
        // write the bundle to the file location
        this.bundleToFile(bundle, probFile);
    }
    
    /**
     * Reads the probabilities stored in the given filepath, converting
     * to a probability bundle
     * @param file path to probability file
     * @return probability bundle converted from probability file
     * @throws IOException 
     */
    private ProbabilityBundle fileToBundle(File file) throws IOException
    {
        ProbabilityBundle bundle;
        try(FileReader reader = new FileReader(file))
        {
            Gson gson = new Gson();
            bundle = gson.fromJson(reader, ProbabilityBundle.class);
        }
        return bundle;
    }
    
    /**
     * Writes the contents of a probability bundle to a file
     * @param bundle bundle whose contents are to be written
     * @param file destination file (will be overwritten if exists)
     * @throws IOException 
     */
    private void bundleToFile(ProbabilityBundle bundle, File file) throws IOException
    {
        try (PrintWriter fileWriter = new PrintWriter(file)) 
        {
            Gson gson = new Gson();
            String json = gson.toJson(bundle);
            fileWriter.write(json);
            fileWriter.flush();
        }
    }
    
    /**
     * Additively trains the probability bundle according to the inputted traces
     * @param traces
     * @param bundle 
     */
    private void train(List<IsotopeTrace> traces, ProbabilityBundle bundle)
    {
        PearsonsCorrelation correlationCalculator = new PearsonsCorrelation();
        
        // sort traces in ascending mz order
        // this allows the alg to remove processed traces
        // and only consider right-hand associations, in turn
        // preventing double counting potential and actual associations
        traces.sort(Comparator.comparing(trace -> trace.centroidMZ));
        
        // arrange traces into bin grid to emulate actual clustering process
        // because P(associated) is actually P(associated | rtoverlap=true, mzDifference < 1.1)
        TraceGrid traceGrid = new TraceGrid(traces);
        
        // for each trace find all neighboring traces, each of which is either
        //  1) the right-hand association
        //  2) unassociated
        for(IsotopeTrace trace : traces)
        {
            if(trace.arc.size() > 1)
            {
                // a trace can only be associated once (right-hand association)
                boolean hasBeenAssociated = false;

                // iterate through each trace within the neighborhood
                // iterates in order of ascending mz, thus first discovered matching
                // trace is the nearest right-hand trace in the envelope
                TreeSet<IsotopeTrace> neighbors = traceGrid.getNeighbors(trace);
                for(IsotopeTrace neighbor : neighbors)
                {
                    if(neighbor.arc.size() > 1)
                    {
                        // mz separation between traces
                        double separation = Math.abs(trace.centroidMZ - neighbor.centroidMZ);

                        // trace arc correlation
                        double[][] alignedArcs = trace.alignArcs(neighbor.arc);
                        double correlation = correlationCalculator.correlation(alignedArcs[0], alignedArcs[1]);

                        // 1) right-hand association
                        if(!trace.envelopeID.equals(0) && trace.envelopeID.equals(neighbor.envelopeID) && !hasBeenAssociated)
                        {
                            bundle.addOccurrence(true, separation, correlation);
                            hasBeenAssociated = true;
                        }

                        // 2) unassociated
                        else 
                            bundle.addOccurrence(false, separation, correlation);
                    }
                }
            }
        }
    }
    
    @Override
    protected ProbabilityBundle initBundle() {
        try {
            return this.fileToBundle(this.probabilityFile);
        } catch (IOException ex) {
            System.err.println("Failed to load " + this.probabilityFile.getAbsolutePath() + " as probability file || " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    protected void populateAssociationCounts(ProbabilityBundle bundle) {
        // no need, file contains probability counts
    }

    @Override
    protected void populateSeparationCounts(ProbabilityBundle bundle) {
        // no need, file contains probability counts
    }

    @Override
    protected void populateCorrelationCounts(ProbabilityBundle bundle) {
        // no need, file contains probability counts
    }
}
