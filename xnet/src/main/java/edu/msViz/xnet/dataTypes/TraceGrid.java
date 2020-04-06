/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet.dataTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * A grid of bins, each bin containing the IDs of all traces landing within the bin
 * Must be bulkloaded: all traces must be known beforehand, no appending traces
 * @author kyle
 */
public class TraceGrid {
    
    /**
     * statically configured bin dimensions
     */
    private static final float RT_BIN_WIDTH = .5f;
    private static final double MZ_BIN_WIDTH = 1.1;
    
    // grid of traceID sets
    private List<IsotopeTrace>[] grid;
    
    // resulting grid shape
    private int numCols;
    private int numRows;
    private double mzMin;
    private double mzMax;
    private float rtMin;
    private float rtMax;
    
    /**
     * Default constructor accepting a list of traces to bin
     * @param traces traces to bin
     */
    public TraceGrid(List<IsotopeTrace> traces)
    {
        // initialize grid
        this.initGrid(traces);
        
        // load trace IDs into grid
        this.populateGrid(traces);
    }
    
    /**
     * configures the grid's dimensions and bounds, instantiates the grid,
     * all based on the set of traces to encapsulate
     * @param traces traces to encapsulate with grid
     */
    @SuppressWarnings("unchecked")
    private void initGrid(List<IsotopeTrace> traces)
    {
        // discover bounds of trace set
        this.mzMin = Double.MAX_VALUE;
        this.mzMax = Double.MIN_VALUE;
        this.rtMin = Float.MAX_VALUE;
        this.rtMax = Float.MIN_VALUE;
        for(IsotopeTrace trace : traces)
        {
            if(trace.centroidMZ < this.mzMin) this.mzMin = trace.centroidMZ;
            if(trace.centroidMZ > this.mzMax) this.mzMax = trace.centroidMZ;
            if(trace.minRT < this.rtMin) this.rtMin = trace.minRT;
            if(trace.maxRT > this.rtMax) this.rtMax = trace.maxRT;
        }
        
        // compute number of columns and rows
        double mzRange = this.mzMax - this.mzMin;
        float rtRange = this.rtMax - this.rtMin;
        this.numCols = (int)Math.ceil(mzRange / MZ_BIN_WIDTH);
        this.numRows = (int)Math.ceil(rtRange / RT_BIN_WIDTH);
        
        //instantiate grid
        this.grid = new List[this.numCols * this.numRows];
    }
    
    /**
     * Loads the traces into the grid, placing each trace's ID in each bin
     * that it overlaps
     * @param traces traces to load into grid
     */
    private void populateGrid(List<IsotopeTrace> traces)
    {
        traces.stream().forEach((trace) -> {
            
            // get trace's column and row indexes
            int[] indexes = this.getTraceIndexes(trace);
            int columnIndex = indexes[0]; int startRowIndex = indexes[1]; int endRowIndex = indexes[2];
            
            // place trace ID in each row within column
            for(int row = startRowIndex; row <= endRowIndex; row++)
            {
                // absolute (1D) index of specified bin
                int absoluteIndex = this.getAbsoluteIndex(row, columnIndex);
                
                // insert trace's ID into bin
                List<IsotopeTrace> bin = this.grid[absoluteIndex];
                if (bin == null) {
                    bin = new ArrayList<IsotopeTrace>(1);
                    this.grid[absoluteIndex] = bin;
                }
                bin.add(trace);
            }
        });
    }
    
    /**
     * Returns the column, start row and end row indexes for the trace
     * @param trace trace whose indexes are to be computed
     * @return  {column, start row, end row}
     */
    public int[] getTraceIndexes(IsotopeTrace trace)
    {
        // mz: column 
        int columnIndex = (int)Math.floor((trace.centroidMZ - this.mzMin) / MZ_BIN_WIDTH);

        // start rt: start row
        // lower bin limit is inclusive when determining start row bin
        int startRowIndex = (int)Math.floor((trace.minRT - this.rtMin) / RT_BIN_WIDTH);

        // end rt: end row
        // upper bin limit is inclusive when determining end row bin
        int endRowIndex = (int)Math.ceil((trace.maxRT - this.rtMin) / RT_BIN_WIDTH) - 1;
        
        return new int[] {columnIndex, startRowIndex, endRowIndex};
    }
    
    /**
     * Returns the absolute (1-dimensional) index corresponding to the row and column indexes
     * @param row 2D row index
     * @param column 2D column index
     * @return the absolute (1-dimensional) index
     */
    private int getAbsoluteIndex(int row, int column)
    {
        // row index * row size + column index
        return row * this.numCols + column;
    }
    
    /**
     * Gets the neighboring traces of the given trace
     * @param trace trace whose neighbors are to be retrieved
     * @return trace's neighboring traces as a tree set, allows in order iteration.
     */
    public TreeSet<IsotopeTrace> getNeighbors(IsotopeTrace trace)
    {
        TreeSet<IsotopeTrace> results = new TreeSet<>(Comparator.comparing(t -> t.centroidMZ));
        
        // get trace's column and row indexes
        int[] indexes = this.getTraceIndexes(trace);
        int columnIndex = indexes[0]; int startRowIndex = indexes[1]; int endRowIndex = indexes[2];
        
        // check neighboring columns and current column
        for(int col = Math.max(0, columnIndex - 1); col <= Math.min(this.numCols - 1, columnIndex + 1); col++)
        {
            // check each overlapping row in the column
            for(int row = startRowIndex; row <= endRowIndex; row++)
            {
                // get the set of IDs in the corresponding bin
                List<IsotopeTrace> tracesInBin = this.grid[this.getAbsoluteIndex(row, col)];
                
                // collect unique
                if (tracesInBin != null) {
                    results.addAll(tracesInBin);
                }
            }
        }
        
        // remove thyself
        results.remove(trace);
        return results;
    }
    
    /**
     * removes the trace's binned IDs from the grid
     * @param trace trace to erase from grid
     */
    public void removeTrace(IsotopeTrace trace)
    {
        // get the trace's indexes
        int[] indexes = this.getTraceIndexes(trace);
        int columnIndex = indexes[0]; int startRowIndex = indexes[1]; int endRowIndex = indexes[2];
        
        // remove the trace ID from each of the trace's overlapping bins
        for(int row = startRowIndex; row <= endRowIndex; row++)
            this.grid[this.getAbsoluteIndex(row, columnIndex)].remove(trace);
    }
}
