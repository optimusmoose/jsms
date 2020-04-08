/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.IO;

import com.opencsv.CSVWriter;
import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.MzTree;
import java.io.FileWriter;
import java.util.List;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Collectors;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import edu.msViz.xnet.TraceClusterer;
import edu.msViz.xnet.probability.ProbabilityAggregator;


/**
 * Facilitates the export of ms data selections from an mzTree to a single CSV file
 * @author kyle
 */
public class CsvExporter implements AutoCloseable{

    /**
     * csv file path, provided upon instantiation
     */
    private final String destinationPath;

    /**
     * length of partitions
     */
    private static final float PARTITION_LENGTH = 5.0f;

    // EPSILON values to cope with floating point accuracy issues
    private static final float EPSILONf = 0.000001f;

    /**
     * csv output writer
     */
    private final CSVWriter outputWriter;

    /**
     * Default constructor, creates a csv file at the given path and writes a header
     * @param filepath csv destination path
     * @throws IOException
     */
    public CsvExporter(String filepath) throws IOException
    {
        //append csv extension if not already there
        if(!filepath.endsWith(".csv"))
            this.destinationPath = filepath + ".csv";
        else
            this.destinationPath = filepath;

        // create csv writer
        outputWriter = new CSVWriter(new FileWriter(this.destinationPath));

        // write header
    }

    /**
     * Exports multiple ranges from a given mzTree
     * @param mzTree mzTree containing points to query and export
     * @return number of points exported
     * @throws IOException
     */
    public int exportTraces(MzTree mzTree) throws IOException
    {
      outputWriter.writeNext(new String[] {"traceID","minMZ", "maxMZ", "minRT","maxRT", "peakMZ", "peakRT", "peakIntensity", "fwhm", "intensitySum",""});

      List<IsotopeTrace> traces = mzTree.bundleTraces().synthesize();
      for(int i = 0; i < traces.size(); ++i){
        double peakMZ = 0;
        double peakRT = 0;
        double maxInt = 0;
        double maxMZ = 0;
        double maxRT = 0;
        double minMZ = Double.POSITIVE_INFINITY;
        double minRT = Double.POSITIVE_INFINITY;

        for(int j = 0; j < traces.get(i).mzValues.size(); ++j){
          float mz = traces.get(i).mzValues.get(j).floatValue();
          float rt = traces.get(i).rtValues.get(j).floatValue();
          float intensity = traces.get(i).intensityValues.get(j).floatValue();

          if(intensity > maxInt){
            peakMZ = mz;
            peakRT = rt;
            maxInt = intensity;
          }
          if(mz < minMZ){
            minMZ = mz;
          }
          if(mz > maxMZ){
            maxMZ = mz;
          }
          if(rt < minRT){
            minRT = rt;
          }
          if(rt > maxRT){
            maxRT = rt;
          }
        }

        outputWriter.writeNext(new String[] {
          Integer.toString(traces.get(i).traceID),
          Double.toString(minMZ),
          Double.toString(maxMZ),
          Double.toString(minRT),
          Double.toString(maxRT),
          Double.toString(peakMZ),
          Double.toString(peakRT),
          Double.toString(maxInt),
          Double.toString(traces.get(i).fwhm()),
          Double.toString(traces.get(i).intensitySum)
        },false);
      }
      return traces.size();
    }
    /**
     * Exports multiple ranges from a given mzTree
     * @param mzTree mzTree containing points to query and export
     * @return number of points exported
     * @throws IOException
     */
    public int exportEnvelopes(MzTree mzTree) throws IOException
    {

      List<IsotopeTrace> traces = mzTree.bundleTraces().synthesize();

      //String envelopeOutput = "monoIsotopicMz,monoIsotopicRT,chargeState,minMZ,maxMZ,minRT,maxRT,totalAbundance,relativeIntensities\n";

     

      outputWriter.writeNext(new String[] { "monoIsotopicMz","monoIsotopicRT","chargeState","minMZ","maxMZ","minRT","maxRT","totalAbundance","relativeIntensities"});

      List<IsotopicEnvelope> Envelopes = new TraceClusterer().clusterTraces(traces, ProbabilityAggregator.PROB_MODEL.BAYESIAN, null);
  
      for(int i = 0; i < Envelopes.size(); i++) {
        String envelopeOutput = "";
        double intensitySum = 0;
        double maxMZ = 0;
        double maxRT = 0;
        double minMZ = Double.POSITIVE_INFINITY;
        double minRT = Double.POSITIVE_INFINITY;
        for (int j = 0; j < Envelopes.get(i).traces.length; j++){
          for(int k = 0; k < Envelopes.get(i).traces[j].mzValues.size(); k++){
            float mz = Envelopes.get(i).traces[j].mzValues.get(k).floatValue();
            float rt = Envelopes.get(i).traces[j].rtValues.get(k).floatValue();
            float intensity = Envelopes.get(i).traces[j].intensityValues.get(k).floatValue();
            int traceID = Envelopes.get(i).traces[j].traceID;
            int envelopeID = Envelopes.get(i).envelopeID;
            if(mz < minMZ){
              minMZ = mz;
            }
            if(mz > maxMZ){
              maxMZ = mz;
            }
            if(rt < minRT){
              minRT = rt;
            }
            if(rt > maxRT){
              maxRT = rt;
            }
          }
          intensitySum+= Envelopes.get(i).traces[j].intensitySum;
        }
        String relativeIntensities = "";
        if(Envelopes.get(i).chargeState > 0){
          for(int l = 0; l < Envelopes.get(i).relativeIntensities.length; l++){
            if(l == Envelopes.get(i).relativeIntensities.length-1){
              relativeIntensities += Envelopes.get(i).relativeIntensities[l];
            }else{
              relativeIntensities += Envelopes.get(i).relativeIntensities[l] + "-";
            }
          }
        }
        //envelopeOutput += Envelopes.get(i).monoisotopicMZ + "," + Envelopes.get(i).monoisotopicRT + "," + Envelopes.get(i).chargeState + "," + minMZ + "," + maxMZ + "," + minRT + "," + maxRT + "," +intensitySum + ",";
        outputWriter.writeNext(new String[] {
          Double.toString(Envelopes.get(i).monoisotopicMZ),
          Double.toString(Envelopes.get(i).monoisotopicRT),
          Double.toString(Envelopes.get(i).chargeState),
          Double.toString(minMZ),
          Double.toString(maxMZ),
          Double.toString(minMZ),
          Double.toString(maxRT),
          Double.toString(intensitySum),
          relativeIntensities
        });
      }
      return Envelopes.size();
    }

    /**
     * Exports multiple ranges from a given mzTree
     * @param ranges ranges to export
     * @param mzTree mzTree containing points to query and export
     * @param onlySegmented if true, export segmented data only
     * @return number of points exported
     * @throws IOException
     */
    public int export(List<MsDataRange> ranges, MzTree mzTree, boolean onlySegmented) throws IOException
    {
        outputWriter.writeNext(new String[] {"m/z","RT","intensity","traceID","envelopeID"});

        int numPoints = 0;
        for(MsDataRange range : ranges)
            numPoints += this.export(range, mzTree, onlySegmented);

        return numPoints;
    }

    /**
     * Exports a single data range from the mzTree to the csv file
     * @param msDataRange range to select from mzTree
     * @param mzTree mzTree containing data points to export
     * @param onlySegmented if true, export segmented data only
     * @return number of data points exported
     * @throws IOException
     */
    public int export(MsDataRange msDataRange, MzTree mzTree, boolean onlySegmented) throws IOException {
        int numPoints = 0;

        // create iterator over range's partitions
        Iterator<MsDataRange> partitionIterator = this.partitionedRange(msDataRange);

        // iterate range's partitions
        while(partitionIterator.hasNext())
        {
            MsDataRange range = partitionIterator.next();

            // TODO: mzTree.query only takes inclusive bounds, so shorten the bound that will be incremented (RT)
            // This could cause a small number of points to be skipped during export. This scenario was considered
            // slightly less bad than duplicating 8000 points when this change was made.
            List<MsDataPoint> partitionResults = mzTree.query(range.mzMin, range.mzMax, range.rtMin, range.rtMax - EPSILONf, 0);

            // if collecting only segmented data, apply a segmented filter
            if(onlySegmented) {
                partitionResults = partitionResults.stream().filter((MsDataPoint point) -> point.traceID != 0 && point.traceID != -1).collect(Collectors.toList());
            }

            // write all points in the stream, count num points written
            for (MsDataPoint p : partitionResults) {
                int envelopeID = mzTree.traceMap.getOrDefault(p.traceID, 0);
                outputWriter.writeNext( new String[] {Double.toString(p.mz), Float.toString(p.rt), Double.toString(p.intensity), Integer.toString(p.traceID), Integer.toString(envelopeID) }, false);
            }
            numPoints += partitionResults.size();
        }

        this.outputWriter.flush();
        return numPoints;
    }

    /**
     * Creates an iterator over the data range's partitions. Iterates along the m/z axis (fixed RT) if the
     * range is wide, otherwise iterates along the RT axis (fixed m/z).
     * @param range ms data range to partition
     * @return Iterator iterator over range's partitions
     */
    private Iterator<MsDataRange> partitionedRange(MsDataRange range)
    {
        // isWide == true -> smaller partitions along m/z axis
        final MsDataRange currentPartition;

        // partition along rt axis
        currentPartition = new MsDataRange(range.mzMin, range.mzMax, range.rtMin, range.rtMin + PARTITION_LENGTH);

        // create iterator for query range's partitions
        Iterator<MsDataRange> partitionIterator = new Iterator<MsDataRange>()
        {
            @Override
            public boolean hasNext() {
                return currentPartition.rtMin <= range.rtMax;
            }

            @Override
            public MsDataRange next()
            {

                MsDataRange returnPartition = new MsDataRange(currentPartition);

                // advance partition for next (will exceed range if final partition, causes hasNext to fail)
                currentPartition.rtMin += PARTITION_LENGTH;
                currentPartition.rtMax += PARTITION_LENGTH;

                // cap the bounds at the requested range
                if (currentPartition.rtMax > range.rtMax) {
                    currentPartition.rtMax = range.rtMax;
                }

                return returnPartition;
            }
        };
        return partitionIterator;
    }

    public String getDestinationPath()
    {
        return this.destinationPath;
    }

    @Override
    public void close() throws Exception {
        this.outputWriter.flush();
        this.outputWriter.close();
    }
}
