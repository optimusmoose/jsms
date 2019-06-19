/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree;

import edu.msViz.mzTree.storage.StorageFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Encapsulates a pointID -> MsDataPoint map, augmenting with data storage interaction
 * @author kyle
 */
public class PointCache 
{
    private static final Logger LOGGER = Logger.getLogger(PointCache.class.getName());
    
    // cache containing msDataPoints keyed by their IDs
    private Map<Integer,MsDataPoint> cache = new ConcurrentHashMap<>();
    
    // limit on the number of entries allowed in the cache
    // estimated according to the java heap size
    private final long sizeLimit;
    
    // StorageFacade object initialized by the mzTree
    private StorageFacade dataStorage;

    /**
     * Default constructor accepting data storage implementation
     * @param dataStorage data storage implementation
     */
    public PointCache(StorageFacade dataStorage)
    {
        // keep reference to data storage
        this.dataStorage = dataStorage;
        
        // cap to 80% of heap
        float allowedRamPercentage = (float).8;
        
        // bytes in heap allowed for point cache
        long numAllowedBytes = (long)(Runtime.getRuntime().maxMemory() * allowedRamPercentage);
        
        // sizeLimit = Heap Size * percentage / bytes per point
        this.sizeLimit = numAllowedBytes / MsDataPoint.MEM_NUM_BYTES_PER_POINT;
        
    }
    
    /**
     * Retrieves the points referenced by pointIDs. First, the cache is queried
     * for any containing points. If the cache does not contain a requested point
     * the database is queried for the point.
     * @param pointIDs
     * @return 
     */
    public ArrayList<MsDataPoint> retrievePoints(List<Integer> pointIDs)
    {
        // points found in cache
        ArrayList<MsDataPoint> points = new ArrayList<>();
        
        // points that cache missed
        ArrayList<Integer> missedPoints = new ArrayList<>();
        
        // separate incoming IDs into hits/misses
        for(int pointID : pointIDs){
            MsDataPoint point;
            if((point = this.cache.get(pointID)) != null)
                points.add(point);
            else
                missedPoints.add(pointID);
        }
        
        // make room in the cache for the missed points
        this.makeRoom(missedPoints.size());
        
        // load points from storage if any to load
        if(missedPoints.size() > 0){
            
            List<MsDataPoint> loadedPoints = null;
            
            try{
                loadedPoints = this.dataStorage.loadPoints(missedPoints);
            }
            catch(Exception ex){
                LOGGER.log(Level.WARNING, "Unable to load points from database", ex);
            }

            if(loadedPoints != null) 
            {
                // cache the loaded points
                this.putAll(loadedPoints);

                // unify point lists and return
                points.addAll(loadedPoints);
            }
        }
        return points;
    }

    public ArrayList<MsDataPoint> retrievePoints(int[] pointIDs) {
        return retrievePoints(Arrays.stream(pointIDs).boxed().collect(Collectors.toList()));
    }

    /**
     * Makes sure there is room in the cache for n more points
     * @param n 
     */
    private void makeRoom(int n)
    {
        // the simple, brute force tactic for now
        // if cache.size() + n > sizeLimit -> clear the cache
        if(this.cache.size() + n > sizeLimit)
            this.cache.clear();
    }
    
    /**
     * Clears the pointCache
     */
    public void clear() {
        this.cache.clear();
    }
    
    /**
     * retrieves a single point from the point cache, retrieving from storage
     * if the point is not currently cached
     * @param pointID ID of point to retrieve
     * @return specified point
     */ 
    public MsDataPoint get(int pointID)
    {
        MsDataPoint point;
        
        // attempt to retrieve MsDataPoint from cache
        if( (point = this.cache.get(pointID)) == null)
        {
            // null point means not in cache, load from db
            try{
                ArrayList<Integer> pointIDWrapper = new ArrayList<>();
                pointIDWrapper.add(pointID);
                this.makeRoom(1);
                point = this.dataStorage.loadPoints( pointIDWrapper ).get(0);
            }
            catch (Exception ex){
                LOGGER.log(Level.WARNING, "Could not load point " + pointID + " from storage", ex);
            }
        }
        
        return point;
    }
    
    /**
     * Inserts a point into the point cache
     * @param point 
     */
    public void put(MsDataPoint point){
        this.cache.put(point.pointID, point);
    }
    
    /**
     * Inserts a list of points into the point cache
     * @param points points to insert into cache
     */
    public void putAll(List<MsDataPoint> points){
        for(MsDataPoint point : points)
            this.cache.put(point.pointID, point);
    }
    
    /**
     * Returns the size of the point cache
     * @return Size of the cache
     */
    public int size(){
        return this.cache.size();
    }
    
    /**
     * Updates the trace ID of a point ONLY if it is cached
     * @param pointID ID of the point to update
     * @param traceID updated trace ID value
     */
    public void shallowTraceUpdate(int pointID, int traceID)
    {
        if(this.cache.containsKey(pointID))
            this.cache.get(pointID).traceID = traceID;
    }
    
}
