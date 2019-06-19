/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.storage;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import edu.msViz.mzTree.ImportState;
import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.MzTreeNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

/**
 *
 * @author Kyle
 */
public interface StorageFacade
{
    /**
     * Initializes persistent storage at the given filepath
     * @param filepath path to output file
     * @param numPoints number of points that will be saved (null if unknown)
     * @throws DataFormatException 
     */
    public void init(String filepath, Integer numPoints) throws Exception;

    /**
     * @return The root node of the mzTree stored in this instance
     * @throws Exception
     */
    public MzTreeNode loadRootNode() throws Exception;

    /**
     * Loads the nodes which are children of the given parent node
     * @throws Exception
     */
    public List<MzTreeNode> loadChildNodes(MzTreeNode parent) throws Exception;

    /**
     * @return A mapping from Trace->Envelope stored in this instance
     * @throws Exception
     */
    public Map<Integer, Integer> loadTraceMap() throws Exception;
    
    /**
     * Copies the data storage to a new location
     * @param targetFilepath
     * @throws Exception 
     */
    public void copy(Path targetFilepath) throws Exception;

    /**
     * Queries the number of points stored
     * @return number of points
     */
    public Integer getPointCount() throws Exception;

    /**
     * Gets the point IDs belonging to the specified node
     * @param nodeID
     * @return node's point IDs
     * @throws Exception 
     */
    public int[] getNodePointIDs(int nodeID) throws Exception;
    
    /**
     * Updates the specified points to have the given traceID
     * @param traceID updated traceID value
     * @param pointIDs IDs of points to update
     * @throws Exception 
     */
    public void updateTraces(int traceID, Integer[] pointIDs) throws Exception;
    
    /**
     * Updates the trace with the given traceID to have the given compiled trace values
     * @param traceID trace identifier
     * @param centroidMZ intensity weighted mz average
     * @param minRT minimum rt value in trace
     * @param maxRT maximum rt value in trace
     * @param intensitySum sum of all intensities
     * @throws Exception 
     */
    public void updateTrace(int traceID, double centroidMZ, float minRT, float maxRT, double intensitySum) throws Exception;
    
    /**
     * Inserts a trace
     * @param traceID ID of the new trace
     * @param envelopeID initial envelope of the trace
     * @throws Exception
     */
    public void insertTrace(int traceID, int envelopeID) throws Exception;
    
    /**
     * Deletes the specified trace
     * @param traceID ID of the trace to delete
     * @throws Exception 
     */
    public void deleteTrace(int traceID) throws Exception;
    
    /**
     * Deletes the specified envelopes from the data store
     * If passed a null pointer deletes ALL of the databases (DESTRUCTION!!!)
     * @param envelopeIDs IDs of the envelopes to delete (null -> delete all envelopes)
     * @throws Exception 
     */
    public void deleteEnvelopes(int[] envelopeIDs) throws Exception;
    
    /**
     * Loads all trace entities from the database
     * @param single if true, returns a single trace
     * @return list of isotope traces
     * @throws Exception 
     */
    public List<IsotopeTrace> loadTraces(boolean single) throws Exception;
    
    /**
     * Updates the envelope IDs of the specified traces
     * @param envelopeID updated envelope ID value
     * @param traceIDs IDs of traces to update
     * @throws Exception 
     */
    public void updateEnvelopes(int envelopeID, Integer[] traceIDs) throws Exception;
    
    /**
     * Saves the node to the storage solution
     * @param node MzTreeNode to save
     * @param parentNodeID node's parentNodeID
     * @return ID of the node saved     
     * @throws Exception 
     */
    public int saveNode(MzTreeNode node, int parentNodeID) throws Exception;
    
    /**
     * Saves the given points to the storage solution
     * @param points MsDataPoints to save
     * @param importState import progress monitor
     * @throws Exception 
     */
    public void savePoints(SavePointsTask task, ImportState importState) throws Exception;
    
    /**
     * Saves NodePoint entities in storage
     * @param curNode recursive cursor
     * @param importState import progress monitro
     * @throws Exception 
     */
    public void saveNodePoints(MzTreeNode curNode, ImportState importState) throws Exception;
    
    /**
     * Loads the requested points from storage
     * @param pointIDs IDs of points to load
     * @return list of MsDataPoints loaded from storage
     * @throws java.lang.Exception
     */
    public List<MsDataPoint> loadPoints(List<Integer> pointIDs) throws Exception;
    
    /**
     * Loads all of the points belonging to the inputted set of leaf mzTreeNodes
     * @param leaves leaf nodes whose points are to be returned
     * @param mzmin
     * @param mzmax
     * @param rtmin
     * @param rtmax
     * @return list of points belonging to leaf nodes
     * @throws java.lang.Exception 
     */
    public List<MsDataPoint> loadLeavesPointsInBounds(List<MzTreeNode> leaves, double mzmin, double mzmax, float rtmin, float rtmax) throws Exception;
    
    /**
     * Performs any commits or updates that are required to flush
     * any potentially pending changes to disk
     * @throws java.lang.Exception
     */
    public void flush() throws Exception;
    
    /**
     * Gets the path to the file containing the persistent save
     * @return the file path to the storage file (if applicable)
     */
    public String getFilePath();
    
    /**
     * Finalizes and closes any resources managed by the storage solution
     */
    public void close();

    /**
     * Inserts the envelope into the data store
     * @param env envelope to insert
     */
    public void insertEnvelope(IsotopicEnvelope env) throws Exception;
    
    public class SavePointsTask {
        public MzTreeNode node;
        public List<MsDataPoint> dataset;
        public SavePointsTask(MzTreeNode inNode, List<MsDataPoint> inDataset)
        {
            this.node = inNode;
            this.dataset = inDataset;
        }
    }
}
