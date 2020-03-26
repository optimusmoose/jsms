/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree;

import edu.msViz.mzTree.IO.MzmlParser;
import com.koloboke.collect.map.hash.HashIntIntMaps;
import com.opencsv.CSVReader;
import edu.msViz.mzTree.ImportState.ImportStatus;
import edu.msViz.mzTree.storage.IntensityTracker;
import edu.msViz.mzTree.storage.StorageFacade;
import edu.msViz.mzTree.storage.StorageFacadeFactory;
import edu.msViz.mzTree.summarization.SummarizationStrategy;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory.Strategy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import javax.xml.stream.XMLStreamException;
import edu.msViz.mzTree.IO.MsDataRange;
import edu.msViz.xnet.TraceClusterer;
import java.io.File;
import java.io.FileReader;

import edu.msViz.xnet.dataTypes.IsotopeTrace;
import edu.msViz.xnet.dataTypes.IsotopicEnvelope;
import edu.msViz.xnet.dataTypes.TracesBundle;
import edu.msViz.xnet.probability.ProbabilityAggregator;


/**
 * R-Tree implementation for storing and accessing MS data
 * @author rob
 */
public final class MzTree
{
    private static final Logger LOGGER = Logger.getLogger(MzTree.class.getName());

    // there is really no good reason for this...
    public static final int DEFAULT_TREE_HEIGHT = 4;

    // number of points per node should assume an whole number of disk blocks
    // some disks ship with blocks of size 4096B, some with blocks of 512B. luckily 512 is a multiple of 4096
    // an MsDataPoint requires 22B, so we need the LCM of 4096 and 22, which is 45056
    // 45056B is 11 4096B disk blocks, 88 512B disk blocks, and 2048 points.
    public static final int NUM_POINTS_PER_NODE = 8192;

    // minimum branching factor for a partitioned load
    // ensures that we don't have a tiny branching factor that produces
    // a very tall tree (such as 2...)
    private static final int MINIMUM_BRANCHING_FACTOR = 4;

    // fraction of heap alloted for MsDataPoints
    private static final float HEAP_FRACTION = .5f;

    // the branching factor of the tree (number of children per root/hidden node)
    public int branchingFactor;

    // the height of the tree
    public short treeHeight;

    // the tree's head node
    public MzTreeNode head;

    // the summarizer used to form the summarized collections of data
    // at the root/intermediate nodes
    public SummarizationStrategy summarizer;

    // point map, keyed by pointID for unified point storage
    public PointCache pointCache;

    // mapping of traceIDs to envelopeIDs
    public Map<Integer,Integer> traceMap;

    // disk storage implementation
    public StorageFacade dataStorage;

    // intensity tracking data
    public boolean trackIntensity = true;
    public IntensityTracker intensityTracker;

    // static storage interface choice
    private static final StorageFacadeFactory.Facades STORAGE_INTERFACE_CHOICE = StorageFacadeFactory.Facades.Hybrid;

    // import progress monitor
    private ImportState importState;

    /**
     * No argument constructor for basic initialization
     * @param importMonitor import progress monitor
     */
    public MzTree() {
        this.traceMap = HashIntIntMaps.newMutableMap();
        this.importState = new ImportState();
    }


    // A custom ConvertDestinationProvider can be set on the MzTree
    // which will be used to determine where to save a converted MzTree file on import of csv or mzML
    public interface ConvertDestinationProvider {
        Path getDestinationPath(Path suggestedFilePath) throws Exception;
    }

    private ConvertDestinationProvider convertDestinationProvider = null;

    public void setConvertDestinationProvider(ConvertDestinationProvider convertDestinationProvider) {
        this.convertDestinationProvider = convertDestinationProvider;
    }

    //***********************************************//
    //                     LOAD                      //
    //***********************************************//

    private Path getConvertDestinationPath(Path sourceFilePath) throws Exception {
        // date time file name
        String suggestedFileName = new SimpleDateFormat("MM-dd-yyyy_HH-mm-ss").format(new java.util.Date()) + ".mzTree";
        Path suggestedFilePath = sourceFilePath.resolveSibling(suggestedFileName);

        // if a ConvertDestinationProvider has been given, use it to determine the output path
        if (convertDestinationProvider == null) {
            return suggestedFilePath;
        } else {
            return convertDestinationProvider.getDestinationPath(suggestedFilePath);
        }
    }

    /**
     *
     * Loads an mzTree either by building from mzML or by reconnecting to an mzTree
     * @param filePath The path to the mzML or mzTree file
     * @param summarizationStrategy strategy to be used for summarization of points
     * @throws Exception
     */
    public void load(String filePath, Strategy summarizationStrategy) throws Exception
    {
        // construct the summarizer corresponding to user's choice
        this.summarizer = SummarizationStrategyFactory.create(summarizationStrategy);

        // create ImportState and set source file location
        this.importState.reset();
        importState.setSourceFilePath(filePath);

        try
        {
            long start = System.currentTimeMillis();

            // csv load
            if(filePath.endsWith(".csv"))
            {
                this.csvLoad(filePath);
            }

            // mzml load
            else
            {
                // initialize mzmlParser
                MzmlParser mzmlParser = new MzmlParser(filePath);

                // if the user specified a memory-conservative load
                // and the partitioned load is necessary then perform a partitioned load
                if(this.partitionedLoadConfiguration(mzmlParser, Paths.get(filePath)))
                {

                    this.partitionedLoad(mzmlParser);
                }

                // else perform standard, memory-apathetic load
                else
                {
                    // initialize mzmlParser
                    this.standardLoad(mzmlParser, filePath);
                }
            }

            importState.setImportStatus(ImportStatus.READY);
            LOGGER.log(Level.INFO, "Tree Build Real Time: " + (System.currentTimeMillis() - start));

        }
        catch (DataFormatException | XMLStreamException ex)
        {
            // try as an mzTree file instead of XML+build in the
            // occurence of DataFormatException or XMLStreamException

            this.traceMap = HashIntIntMaps.newMutableMap();

            // initialize data storage on mzTree file
            this.initDataStorage(STORAGE_INTERFACE_CHOICE, filePath, null);

            // inform the importState of an .mzTree load
            this.importState.setImportStatus(ImportStatus.LOADING_MZTREE);

            // create mzTree from existing file

            // recursively build tree from root node
            this.head = dataStorage.loadRootNode();
            this.recursiveTreeBuilder(this.head, 0);

            // load trace map
            this.traceMap = dataStorage.loadTraceMap();

            // load intensity tracking data
            if (new File(filePath + "-intensity").exists()) {
                this.intensityTracker = new IntensityTracker(filePath + "-intensity", pointCache);
            }

            // inform importState that mzTree load has finished
            this.importState.setImportStatus(ImportStatus.READY);
        }
    }

    /**
     * Recursively builds the tree structure starting with the root node.
     * Retrieves and constructs the MzTreeNode specified by the
     * @param node Node from which to start recursive MzTree construction
     */
    private void recursiveTreeBuilder(MzTreeNode node, int curDepth) throws Exception
    {
        // get all child nodes
        List<MzTreeNode> childNodes = dataStorage.loadChildNodes(node);

        // leaf nodes update tree height (results in largest height)
        if(childNodes.isEmpty())
            this.treeHeight = (curDepth > this.treeHeight) ? (short)curDepth : this.treeHeight;

        // recurse on each child node if there are any
        for(MzTreeNode childNode : childNodes)
        {
            // recursive call at +1 depth
            this.recursiveTreeBuilder(childNode, curDepth+1);

            // add reference to child and keep min/max mz/rt/int
            node.addChildGetBounds(childNode);
        }
    }

    /**
     * Performs a partitioned load of the data set, resulting in conservative memory consumption
     * @param mzmlParser input mzml file parser
     * @throws XMLStreamException
     * @throws DataFormatException
     * @throws IOException
     */
    private void partitionedLoad(MzmlParser mzmlParser) throws XMLStreamException, DataFormatException, IOException
    {
        LOGGER.log(Level.INFO, "Partitioned load w/ " + this.branchingFactor + " partitions");

        // signal to the import monitor that tree building has begun
        this.importState.setImportStatus(ImportStatus.CONVERTING);

        // init head node
        this.head = new MzTreeNode(this.branchingFactor);

        if (trackIntensity) {
            this.intensityTracker = new IntensityTracker(dataStorage.getFilePath() + "-intensity", pointCache);
            this.intensityTracker.setRunCount(this.branchingFactor);
            importState.setImportStatus(ImportStatus.INDEXING);
        }

        // iterate through each level 1 node, loading partition and
        // constructing separately

        for(int i = 0; i < this.branchingFactor; i++)
        {
            // current level 1 node
            MzTreeNode curL1Node = new MzTreeNode(this.branchingFactor);

            // load level 1 node's partition
            List<MsDataPoint> curPartition = mzmlParser.readPartition();

            // recursively construct level 1 node
            this.divide(true, curPartition, curL1Node , 1);

            // add the level 1 node to the root node
            this.head.addChildGetBounds(curL1Node);

            if (trackIntensity) {
                curPartition.sort(Comparator.comparingDouble((MsDataPoint p) -> p.intensity).reversed());
                intensityTracker.addRun(curPartition.stream().mapToInt(p -> p.pointID).toArray(), this.importState, this.branchingFactor);
            }

            LOGGER.log(Level.INFO, "Completed partition " + i);
        }

        if (trackIntensity) {
            intensityTracker.finishAdding();
        }

        // root node summarization!!!!!!
        this.head.summarizeFromChildren(MzTree.NUM_POINTS_PER_NODE, this.summarizer, this.pointCache);

        // recursively save node information (only points are saved during construction)
        this.recursiveNodeSave(this.head, 0);

        try {
            // commit all entries
            this.dataStorage.flush();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not flush entries to storage", ex);
        }
    }

    /**
     * Performs a standard, memory-apathetic load
     * @param mzmlParser input file parser initialized w/ target file
     * @throws IOException
     * @throws XMLStreamException
     * @throws DataFormatException
     */
    private void standardLoad(MzmlParser mzmlParser, String filePath) throws Exception
    {
        importState.setImportStatus(ImportStatus.PARSING);

        List<MsDataPoint> dataset = mzmlParser.readAllData();

        this.buildTreeFromRoot(dataset, Paths.get(filePath));
    }

    /**
     * Loads MS data in csv format (mz, rt, intensity, traceID, envelopeID)
     * @param filePath path to csv file
     * @throws FileNotFoundException
     * @throws IOException
     * @throws Exception
     */
    private void csvLoad(String filePath) throws FileNotFoundException, IOException, Exception
    {
        this.importState.setImportStatus(ImportStatus.PARSING);

        ArrayList<MsDataPoint> points = new ArrayList<>();

        // temporary trace map
        Map<Integer,Integer> tempTraceMap = HashIntIntMaps.newMutableMap();

        // open csv reader on targetted csv file
        try(CSVReader reader = new CSVReader(new FileReader(filePath))) {

            String[] line = reader.readNext();

            // parse first line, but let it fail in case it is a header row
            if(line != null)
            {
                try {
                    // convert to msdatapoint, collect
                    MsDataPoint point = this.csvRowToMsDataPoint(line);
                    points.add(point);

                    // add the trace if it doesn't already exist and isn't zero
                    if(point.traceID != 0 && !tempTraceMap.containsKey(point.traceID))
                        tempTraceMap.put(point.traceID, Integer.parseInt(line[4]));
                } catch (NumberFormatException ex) {
                    // assuming the first row was a header row, continue
                }
            }

            // read the remaining lines (now guaranteed no header)
            while((line = reader.readNext()) != null)
            {
                // convert to msdatapoint, collect
                MsDataPoint point = this.csvRowToMsDataPoint(line);
                points.add(point);

                // add the trace if it doesn't already exist
                if(point.traceID != 0 && !tempTraceMap.containsKey(point.traceID))
                    tempTraceMap.put(point.traceID, Integer.parseInt(line[4]));
            }
        }

        // build that tree!
        this.buildTreeFromRoot(points, Paths.get(filePath));

        // insert the traces into the trace map and data storage
        for(Integer traceID : tempTraceMap.keySet())
        {
            this.insertTrace(traceID, tempTraceMap.get(traceID));
        }

    }

    /**
     * Constructs an MzTree from the dataset, starting at the root node (so no partitioned load)
     * @param dataset
     */
    private void buildTreeFromRoot(List<MsDataPoint> dataset, Path sourceFilePath) throws Exception
    {
        LOGGER.log(Level.INFO, "Building MzTree from " + dataset.size() + " points");

        // inform importState of anticipated amount of work
        int numPointsToSave = dataset.size(); // save points: dataset.length
        this.importState.setTotalWork(numPointsToSave);
        this.importState.setImportStatus(ImportStatus.CONVERTING);

        this.initDataStorage(STORAGE_INTERFACE_CHOICE, getConvertDestinationPath(sourceFilePath).toString(), dataset.size());

        // **************** STEP 1: CONFIGURE TREE ****************

        // number of leafNodes = globalNumPoints / hdBlockTupleCapacity
        int numLeafNodes = (int) Math.ceil((float) dataset.size() / (float) MzTree.NUM_POINTS_PER_NODE);

        this.treeHeight = MzTree.DEFAULT_TREE_HEIGHT;

        // branching factor = leafnodes ^ (1/treeDepth)
        this.branchingFactor = (int) Math.ceil(Math.pow(numLeafNodes, 1.0 / (double) this.treeHeight));

        // init head node
        this.head = new MzTreeNode(this.branchingFactor);


        // **************** STEP 2: BUILD ****************

        // divide the head node, do not sort at start (null), mzML data already sorted by RT
        this.divide(null, dataset, this.head, 0);


        if (trackIntensity) {
          importState.setImportStatus(ImportStatus.INDEXING);
          this.intensityTracker = new IntensityTracker(dataStorage.getFilePath() + "-intensity", pointCache);
          this.intensityTracker.setRunCount(1);
          dataset.sort(Comparator.comparingDouble((MsDataPoint p) -> p.intensity).reversed()); // work point 1

          this.intensityTracker.addRun(dataset.stream().mapToInt(p -> p.pointID).toArray(), this.importState, 1); // work point 2

          this.intensityTracker.finishAdding(); // work point 3, ignore for status update
        }

        // recursively save node information (only points are saved during construction)
        this.importState.setTotalWork(numLeafNodes);
        this.importState.setWorkDone(0);
        this.importState.setImportStatus(ImportStatus.WRITING);
        this.recursiveNodeSave(this.head, 0);

        try {
            // commit all entries
            this.dataStorage.flush();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not persist data to storage", ex);
        }
    }

    /**
     * Recursively divides the dataset into the mzTree, a depth first construction
     * starting with the head node.
     * @param sort_by_rt sorting flag, rt or mz
     * @param dataset The recursive call's data partition
     * @param head The recursive call's top level node
     * @param curHeight current height in three (root is 0)
     */
    private void divide(Boolean sort_by_rt, List<MsDataPoint> dataset, MzTreeNode head, int curHeight)
    {
        // leaf flag
        boolean isLeaf = dataset.size() <= MzTree.NUM_POINTS_PER_NODE;

        // LEAF: save points, get mins/maxes
        if (isLeaf)
        {
            // leaf node submits its dataset to be written to data store
            try{
                this.dataStorage.savePoints(new StorageFacade.SavePointsTask(head,dataset), this.importState);
            }
            catch(Exception e)
            {
                LOGGER.log(Level.WARNING, "Could not save points to datastorage for leaf node: " + head.toString(), e);
            }

            // collect point IDs, mz/rt/intensity min/max
            head.initLeaf(dataset);

            dataset = null; // garbage collect away
        }

        // ROOT/INTERMEDIATE: summarize, partition and recurse
        else
        {

            // if sort_by_rt is null then don't sort, implies initial partition
            // on mzml sourced data which is already sorted by RT
            if(sort_by_rt != null)
            {
                if (sort_by_rt)
                    Collections.sort(dataset, Comparator.comparingDouble((MsDataPoint dataPoint) -> dataPoint.rt));
                else
                    Collections.sort(dataset, Comparator.comparingDouble((MsDataPoint dataPoint) -> dataPoint.mz));
            }

            // the partition size is the subset length divided by the numChildrenPerNode
            int partitionSize = (int) Math.ceil((double) dataset.size() / (double) this.branchingFactor);

            // split the dataset into partitions
            List<List<MsDataPoint>> partitions = new ArrayList<>();
            int i = 0;
            while(i < dataset.size())
            {
                // populate partition
                final List<MsDataPoint> partition = dataset.subList(i, Math.min(i + partitionSize, dataset.size()));
                i += partition.size();

                // collect partition
                partitions.add(partition);
            }

            // free dataset for GC
            dataset = null;

            // distribute the partitions to child nodes
            for(List<MsDataPoint> partition : partitions)
            {
                // instantiate child node
                MzTreeNode child = new MzTreeNode(this.branchingFactor);

                // resolve sort_by_rt
                Boolean my_sort_by_rt = sort_by_rt;

                // if null (initial call mzML) set to true
                if(my_sort_by_rt == null)
                    my_sort_by_rt = true;

                // recursively divide child node (depth first)
                this.divide(!my_sort_by_rt, partition, child, curHeight + 1);

                // collect child node
                head.addChildGetBounds(child);
            }

            // collect summary of points from child nodes (additionally saves pointIDs)
            head.summarizeFromChildren(MzTree.NUM_POINTS_PER_NODE, this.summarizer, this.pointCache);
            pointCache.clear();

        } // END ROOT/INTERMEDIATE NODE

    }

    /**
     * Upon the user selecting a memory-conservative load, configures the tree for
     * a partitioned load according to available memory.
     *      - A minimum branching factor must be reached by the partitioned configuration,
     *        a branching factor smaller than the minimum creates a very tall tree.
     *        Increasing the branching factor decreases partition size,
     *        thus not endangering memory consumption.
     *      - If the partitioned configuration returns a branching factor of 1
     *        then the entire dataset will fit into memory. Revert to default configuration.
     * @param mzmlParser input mzml file parser
     * @return false if partitioned load is unnecessary (entire dataset will fit in RAM), otherwise true
     * @throws XMLStreamException
     * @throws FileNotFoundException
     * @throws IOException
     * @throws DataFormatException
     */
    private boolean partitionedLoadConfiguration(MzmlParser mzmlParser, Path sourceFilePath) throws Exception
    {

        this.importState.setImportStatus(ImportStatus.PARSING);

        // count the number of points in the mzML file
        int numPoints = mzmlParser.countPoints();

        // number of available bytes in java heap
        long numBytesInHeap = Runtime.getRuntime().maxMemory();

        // max allowed points to hold in memory at a time
        // = (heap size * FRACTION) / bytes per point
        int maxPointsInRam = (int) Math.floor((numBytesInHeap * MzTree.HEAP_FRACTION) / (float)MsDataPoint.MEM_NUM_BYTES_PER_POINT);

        // number of leafNodes = globalNumPoints / hdBlockTupleCapacity
        int numLeafNodes = (int) Math.ceil((float) numPoints / (float) MzTree.NUM_POINTS_PER_NODE);

        // branching factor determined by the max number of points allowed in RAM
        // if branchingFactor != 1 each level 1 subtree will be processed one at a time
        this.branchingFactor = (short)Math.ceil((float)numPoints / (float)maxPointsInRam);

        // branchingFactor != 1 implies the entire dataset won't fit into the heap,
        // branchingFactor determines treeHeight
        if(this.branchingFactor != 1)
        {
            // ensure we use at least the minimum branching factor
            this.branchingFactor = Math.max(this.branchingFactor, MzTree.MINIMUM_BRANCHING_FACTOR);

            // treeHeight = log_branchingFactor(numLeafNodes)
            // cool logarithmic identity: logb(n) = log(n) / log(b)
            this.treeHeight = (short)(Math.ceil(Math.log(numLeafNodes) / Math.log(this.branchingFactor)));

            // if branchingFactor unchanged by max call partitionSize == maxPointsInRam
            // else partitionSize < maxPointsInRam -> SAFE
            int partitionSize = (int) Math.floor( (float) numPoints / (float) this.branchingFactor);

            // prepare parser for partitioned read
            // recalculate partition size
            mzmlParser.initPartitionedRead(partitionSize);

            this.initDataStorage(STORAGE_INTERFACE_CHOICE, getConvertDestinationPath(sourceFilePath).toString(), numPoints);

            // inform importState of the amount of work to do
            this.importState.setTotalWork(numPoints);

            return true;
        }
        // else branching factor is 1, implying the entire dataset can fit into heap
        // return false to signal a regular load should ensue
        else
            return false;

    }

    /**
     * Recursively saves an mzTree starting at curNode
     * @param curNode node to recursively save
     * @param parentNodeID ID of the node's parent (0 if no parent)
     */
    private void recursiveNodeSave(MzTreeNode curNode, int parentNodeID)
    {
        try{
            // save node to db
            curNode.nodeID = this.dataStorage.saveNode(curNode, parentNodeID);

            // save node points to db
            this.dataStorage.saveNodePoints(curNode, this.importState);

            // update status
            this.importState.setWorkDone(this.importState.getWorkDone()+1);

            // recurse on chilren
            for(MzTreeNode childNode : curNode.children)
                this.recursiveNodeSave(childNode, curNode.nodeID);

        }
        catch(Exception e){
            LOGGER.log(Level.WARNING, "Could not save node", e);
        }

    }

    public ImportState getImportState() {
        return importState;
    }

    public ImportState.ImportStatus getLoadStatus() {
        return importState.getImportStatus();
    }

    public String getLoadStatusString() {
        return importState.getStatusString();
    }

    /**
     * inits the data storage module
     * @param storageChoice Storage interface selection
     * @param filePath (optional) location to create storage file
     * @param numPoints (Hybrid only) number of points that will be saved in file
     * @throws Exception
     */
    private void initDataStorage(StorageFacadeFactory.Facades storageChoice, String filePath, Integer numPoints) throws Exception
    {
        // init data storage module
        this.dataStorage = StorageFacadeFactory.create(storageChoice);
        this.dataStorage.init(filePath, numPoints);
        this.pointCache = new PointCache(this.dataStorage);

        this.importState.setMzTreeFilePath(this.dataStorage.getFilePath());
    }

    //***********************************************//
    //                    QUERY                      //
    //***********************************************//

    /**
     * Queries the MzTree for points contained with the mz, rt bounds
     *
     * @param mzMin query mz lower bound
     * @param mzMax query mz upper bound
     * @param rtMin query rt lower bound
     * @param rtMax query rt upper bound
     * @param numPoints number of points to be returned; 0 to return all points possible from the leaf depth and not use the cache
     * @return 2-dimensional double array
     */
    public List<MsDataPoint> query(double mzMin, double mzMax,
                                   float rtMin, float rtMax, int numPoints)
    {
        boolean useSummary = (numPoints > 0);

        // if zero passed for any query bound use global min/max
        mzMin = (mzMin == 0) ? this.head.mzMin : mzMin;
        mzMax = (mzMax == 0) ? this.head.mzMax : mzMax;
        rtMin = (rtMin == 0) ? this.head.rtMin : rtMin;
        rtMax = (rtMax == 0) ? this.head.rtMax : rtMax;

        // current level in tree
        int curLevel = 0;

        // all nodes in current level of tree within the query bounds
        ArrayList<MzTreeNode> curLevelNodesInBounds = new ArrayList<>();

        // IDs the points in the current level that are within the query bounds
        ArrayList<MsDataPoint> curLevelPointsInBounds = new ArrayList<>();

        // follow down the tree all nodes within the query bounds
        // base case: curLevel is the leaf level
        while(curLevel != this.treeHeight + 1){

            // populates curLevelNodesInBounds with the children of the current curLevelNodesInBounds
            // that are within the query's bounds
            curLevelNodesInBounds = this.collectNextLevelNodesInBounds(curLevelNodesInBounds,mzMin,mzMax,rtMin,rtMax);
            curLevel++;

            if (useSummary) {
                // find candidate points at current level
                curLevelPointsInBounds = this.collectPointsWithinBounds(curLevelNodesInBounds,mzMin,mzMax,rtMin,rtMax);

                // stop going down the tree early if enough points are found
                if (curLevelPointsInBounds.size() >= numPoints) {
                    break;
                }
            }
        }

        if(useSummary) {
            // when using summary, the points have been collected and need to be summarized

            if(curLevelPointsInBounds.size() <= numPoints) {
                // return all points if there are not enough to summarize
                return curLevelPointsInBounds;
            } else {
                // return points sampled down using a summary
                return this.summarizer.summarize(curLevelPointsInBounds, numPoints);
            }
        } else {
            // when not using summary, the points must be loaded from the leaf level

            try {
                // populate each node's pointID array
                for(MzTreeNode node : curLevelNodesInBounds)
                    ensurePointIDs(node);

                // use the leaf-node optimized query
                return this.dataStorage.loadLeavesPointsInBounds(curLevelNodesInBounds, mzMin, mzMax, rtMin, rtMax);
            } catch(Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load points from the leaf level", e);
                return null;
            }
        }
    }

    /**
     * Collects all child nodes of all nodes in curLevelNodesInBounds that overlap
     * with the bounds of the query
     * @param curLevelNodesInBounds nodes in the current level that overlap with the query bounds
     * @param mzMin mz lower bound
     * @param mzMax mz upper bound
     * @param rtMin rt lower bound
     * @param rtMax rt upper bound
     * @return ArrayList containing nodes of the next level that overlap with the query bounds
     */
    private ArrayList<MzTreeNode> collectNextLevelNodesInBounds(ArrayList<MzTreeNode> curLevelNodesInBounds, double mzMin, double mzMax, float rtMin, float rtMax){

        // nodes in the next level that overlap the bounds of the query
        ArrayList<MzTreeNode> nextLevelNodesInBounds = new ArrayList<>();

        // base case: curLevelNodesInBounds is empty
        // return ArrayList with the head node of the tree (guaranteed to overlap  bounds)
        if(curLevelNodesInBounds.isEmpty()){
            nextLevelNodesInBounds.add(this.head);
            return nextLevelNodesInBounds;
        }

        // iterate through all current level nodes
        for(MzTreeNode curLevelNode : curLevelNodesInBounds)
        {
            // if there are children check each child for overlapping bounds
            if (!curLevelNode.children.isEmpty())
            {
                // iterate through current level node's children
                for(MzTreeNode nextLevelNode : curLevelNode.children)
                {
                    // collecting if overlaps with the bounds of the query
                    if(this.doesOverlap(nextLevelNode, mzMin, mzMax, rtMin, rtMax))
                        nextLevelNodesInBounds.add(nextLevelNode);
                }
            }

            // else this is a leaf node not at the expected leaf level,
            // include in next level
            else
                nextLevelNodesInBounds.add(curLevelNode);
        }
        return nextLevelNodesInBounds;
    }

    /**
     * Collects the points within a collection of MzTreeNodes that fall within
     * the given mz/rt bounds
     * @param nodes nodes whose points are to be checked against bounds of query
     * @param mzMin mz lower bound
     * @param mzMax mz upper bound
     * @param rtMin rt lower bound
     * @param rtMax rt upper bound
     * @return List of MsDataPoints belonging to the given nodes that are within the given bounds
     */
    private ArrayList<MsDataPoint> collectPointsWithinBounds(ArrayList<MzTreeNode> nodes,
                                                             double mzMin, double mzMax, float rtMin, float rtMax){

        // collect all point IDs from all nodes
        ArrayList<Integer> allNodesPointIDs = new ArrayList<>();
        for(MzTreeNode node : nodes) {
            ensurePointIDs(node);

            allNodesPointIDs.addAll(Arrays.stream(node.pointIDs).boxed().collect(Collectors.toList()));
        }

        // retrieve all points from pointCache
        ArrayList<MsDataPoint> allNodesPoints = this.pointCache.retrievePoints(allNodesPointIDs);

        // arraylist for collecting points that fall within bounds
        ArrayList<MsDataPoint> pointsWithinBounds = new ArrayList<>();

        // iterate through all nodes, checking to see if they fall within the bounds
        for(MsDataPoint pointToCheck : allNodesPoints){

            // if in bounds then collect
            if(pointToCheck.isInBounds(mzMin, mzMax, rtMin, rtMax))
                pointsWithinBounds.add(pointToCheck);
        }

        return pointsWithinBounds;
    }

    private void ensurePointIDs(MzTreeNode node) {
        if (node.pointIDs == null) {
            // node.pointIDs is lazy loaded on first access, not on file open
            try {
                node.pointIDs = this.dataStorage.getNodePointIDs(node.nodeID);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve points for node. Future query results may be incomplete.", e);
            }
        }
    }

    /**
     * Checks if the search bounds overlap with a node's data bounds
     * @param node node to check
     * @param mzMin mz lower bound
     * @param mzMax mz upper bound
     * @param rtMin rt lower bound
     * @param rtMax rt upper bound
     * @return True if node's bounds overlap search bounds, false otherwise
     */
    private boolean doesOverlap(MzTreeNode node, double mzMin, double mzMax, float rtMin, float rtMax)
    {
        return  // bounds overlap in mz
                (node.mzMin <= mzMax && node.mzMax >= mzMin) &&
                // bounds overlap in rt
                (node.rtMin <= rtMax && node.rtMax >= rtMin);
    }

    //***********************************************//
    //                 SEGMENTATION                  //
    //***********************************************//

    /**
     * Updates the points specified by pointIDs to have the given traceID
     * @param traceID updated traceID value
     * @param pointIDs IDs of points to update
     * @throws java.lang.Exception
     */
    public void updateTraces(int traceID, Integer[] pointIDs) throws Exception
    {
        // update the trace of each point specified in pointIDs
        for(int i = 0; i < pointIDs.length; i++)
            this.pointCache.shallowTraceUpdate(pointIDs[i], traceID);


        this.dataStorage.updateTraces(traceID, pointIDs);

    }

    /**
     * Creates a trace in the traceMap and storage
     * @param traceID ID of the trace to add
     * @param envelopeID initial envelope for the trace
     * @throws java.lang.Exception
     *
     */
    public void insertTrace(int traceID, int envelopeID) throws Exception
    {
        this.traceMap.put(traceID, envelopeID);

        this.dataStorage.insertTrace(traceID, envelopeID);
    }

    /**
     * Deletes a trace from the traceMap and storage
     * @param traceID ID of the trace to delete
     * @throws java.lang.Exception
     */
    public void deleteTrace(int traceID) throws Exception
    {
        // delete trace from traceMap
        this.traceMap.remove(traceID);

        // delete trace from storage
        this.dataStorage.deleteTrace(traceID);
    }

    public void deleteTraces() throws Exception
    {
        // delete trace from storage
        this.dataStorage.deleteTraces();
    }
    /**
     * Updates the envelopeID of the specified traces
     * @param envelopeID new envelope ID value
     * @param traceIDs IDs of the traces to update
     * @throws java.lang.Exception
     */
    public void updateEnvelopes(int envelopeID, Integer[] traceIDs) throws Exception
    {

        // update each trace in the trace map
        for(int i = 0; i < traceIDs.length; i++)
            this.traceMap.put(traceIDs[i], envelopeID);

        // update traces in storage
        this.dataStorage.updateEnvelopes(envelopeID, traceIDs);
    }

    //***********************************************//
    //                    CSV EXPORT                 //
    //***********************************************//

    public void saveAs(Path targetFilepath) throws Exception {
        // source file path
        Path sourceFilepath = Paths.get(this.dataStorage.getFilePath());

        try{
            // close current storage connection
            this.dataStorage.close();

            // copy current output location to new output location
            this.dataStorage.copy(targetFilepath);

            // init connection to new database
            this.dataStorage.init(targetFilepath.toString(), null);

            // copy intensity tracking data
            Path newIntensityFile = Paths.get(targetFilepath + "-intensity");
            Files.copy(Paths.get(sourceFilepath + "-intensity"), newIntensityFile);
            this.intensityTracker = new IntensityTracker(newIntensityFile.toString(), this.pointCache);
        }
        catch(Exception e){
            LOGGER.log(Level.WARNING, "Could not create copy at " + targetFilepath.toString(), e);
            try{
                // revert back to previous connection
                this.dataStorage.init(sourceFilepath.toString(), null);
            }
            catch(Exception ex){
                LOGGER.log(Level.WARNING, "After failed copy, could not revert back to " + sourceFilepath.toString(), ex);
            }
            throw e;
        }
    }

    /**
     * If import status is ready, returns mz x rt bounds in order: mzmin, mzmax, rtmin, rtmax
     * Else returns null
     * @return mz x rt bounds (mzmin, mzmax, rtmin, rtmax) or null
     */
    public MsDataRange getDataBounds()
    {
        // if not ready, then cannot access data bounds
        if(importState.getImportStatus() != ImportStatus.READY)
            return null;

        else
            return new MsDataRange(this.head.mzMin, this.head.mzMax, this.head.rtMin, this.head.rtMax);
    }

    //***********************************************//
    //              TRACE PROCESSING                 //
    //***********************************************//

    /**
     * Collects trace point data into a bundle organized by trace
     * @return trace data bundle
     */
    public TracesBundle bundleTraces()
    {
        TracesBundle tracesBundle = new TracesBundle();

        // query through dataset with slices along mz axis
        final double mzWindowWidth = 8.0;
        for(double mzWindowStart = this.head.mzMin; mzWindowStart < this.head.mzMax; mzWindowStart += mzWindowWidth)
        {
            double mzWindowEnd = Math.min(mzWindowStart + mzWindowWidth, this.head.mzMax);

            List<MsDataPoint> points = this.query(mzWindowStart, mzWindowEnd, this.head.rtMin, this.head.rtMax, 0);

            // add each point to the bundle (filter out noise and unsegmented)
            points.stream().filter(point -> point.traceID != 0 && point.traceID != -1).forEach((point) -> {
                Integer envelope = this.traceMap.get(point.traceID);
                if (envelope == null) {
                    envelope = 0;
                }
                tracesBundle.addPoint(point.traceID,point.mz,point.rt,point.intensity, envelope);
            });
        }

        return tracesBundle;
    }

    /**
     * Saves compiled traces to the database
     * Combine with bundleTraces().synthesize() if traces need to be compiled
     * @param traces compiled traces to save to database
     * @throws Exception
     */
    public void saveCompiledTraces(List<IsotopeTrace> traces) throws Exception
    {
        traces.stream().forEach((trace) -> {
            try {
                this.dataStorage.updateTrace(trace.traceID,trace.centroidMZ,trace.minRT,trace.maxRT,trace.intensitySum);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not update trace's compiled values", ex);
            }
        });
        this.dataStorage.flush();
    }

    /**
     * performs the clustering command specified by the data server
     * @param sModel (String) selected model
     * @param isTrain true if training run specified, else false
     * @param filePath path to file to use as probability model when frequentist/hybrid clustering,
     *          or if training location of resulting probability file
     * @return message describing result of clustering command
     * @throws java.lang.Exception
     */
    public String executeClusteringCommand(String sModel, boolean isTrain, String filePath) throws Exception
    {
        String returnMessage;

        long start = System.currentTimeMillis();

        // scrape dataset for traces data
        List<IsotopeTrace> traces = this.bundleTraces().synthesize();

        // since we have the trace data anyhow,
        // let's save it if it has yet to be saved
        if(!this.tracesHaveBeenCompiled())
        {
            this.saveCompiledTraces(traces);
        }

        // determine the model choice
        ProbabilityAggregator.PROB_MODEL model = ProbabilityAggregator.PROB_MODEL.BAYESIAN;
        model = sModel.equalsIgnoreCase("frequentist") ? ProbabilityAggregator.PROB_MODEL.FREQUENTIST : model;
        model = sModel.equalsIgnoreCase("hybrid") ? ProbabilityAggregator.PROB_MODEL.HYBRID : model;

        // probability training
        if(model != ProbabilityAggregator.PROB_MODEL.BAYESIAN && isTrain)
        {
            new TraceClusterer().trainProbabilities(traces, filePath);

            long end = System.currentTimeMillis();

            returnMessage = "Trained on " + traces.size() + " traces\nSaved to "
                    + filePath + "\nElapsed time (ms): " + (end - start);
        }

        // clustering
        else
        {
            // cluster the traces
            List<IsotopicEnvelope> envelopes = new TraceClusterer().clusterTraces(traces,model,filePath);

            LOGGER.log(Level.INFO, "Writing " + envelopes.size() + " envelopes to database");

            // delete all existing envelopes and envelope references
            this.dataStorage.deleteEnvelopes(null);

            // TODO: do this update as a single DB transaction instead of one per envelope plus one per trace

            // update the database to reflect the clustering assignments
            for(IsotopicEnvelope env : envelopes)
            {
                this.dataStorage.insertEnvelope(env);
                this.updateEnvelopes(env.envelopeID, env.traceIDs);
            }

            long end = System.currentTimeMillis();

            returnMessage = "Traces discovered: " + traces.size() + "\nEnvelopes created: " + envelopes.size()
                + "\nElapsed time (ms): " + (end - start);
        }

        return returnMessage;
    }

    /**
     * Returns true if traces in database have been compiled, false otherwise
     * @return true if traces in database have been compiled, false otherwise
     * @throws Exception
     */
    public boolean tracesHaveBeenCompiled() throws Exception
    {
        // probe database for a trace
        List<IsotopeTrace> results = this.dataStorage.loadTraces(true);

        // first ensure there are traces
        if(!results.isEmpty())
        {
            // if compiled trace attributes are null return false, else return true
            IsotopeTrace trace = results.get(0);
            return trace.centroidMZ != null;
        }

        // no traces means no compilation, return false
        else
            return false;
    }

    public List<IsotopeTrace> recompileTraces() throws Exception {
        List<IsotopeTrace> traces = this.bundleTraces().synthesize();
        this.saveCompiledTraces(traces);
        return traces;
    }

    //***********************************************//
    //                   HELPERS                     //
    //***********************************************//

    /**
     * Converts a csv row MsDataPoint to MsDataPoint object
     * @param line
     * @return
     */
    private MsDataPoint csvRowToMsDataPoint(String[] line)
    {
        double mz = Double.parseDouble(line[0]);
        float rt = Float.parseFloat(line[1]);
        double intensity = Double.parseDouble(line[2]);
        int traceID = Integer.parseInt(line[3]);
        MsDataPoint point = new MsDataPoint(0, mz, rt, intensity);
        point.traceID = traceID;
        return point;
    }

    public void close()
    {
        if (this.dataStorage != null) {
            this.dataStorage.close();
            dataStorage = null;
        }

        if (this.intensityTracker != null) {
            this.intensityTracker.close();
            intensityTracker = null;
        }
    }
}
