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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;

import com.koloboke.collect.map.hash.HashIntIntMaps;

/**
 * StorageFacade implementation that utilized SQLite for persistance
 * @author Kyle
 */
public class HybridStorage implements StorageFacade
{
    private static final Logger LOGGER = Logger.getLogger(HybridStorage.class.getName());

    // connection to the SQLite database
    private Connection dbConnection;

    // SQL statement preparation and execution
    private SQLEngine dbEngine;

    // point value access
    private PointEngine pointEngine;

    // path to the database and point files
    private String filePath;
    private String pointFilePath;

    // work done counter (number of points || nodepoints saved)
    private int workDone = 0;

    //**********************************************//
    //                    INIT                      //
    //**********************************************//

    @Override
    public void init(String filePath, Integer numPoints) throws Exception
    {

        // generate output file location if none passed
        if(filePath == null)
        {
            throw new IllegalArgumentException("filePath must be specified");
        }

        this.filePath = filePath;
        this.pointFilePath = filePath + "-points";

        try{
            // link the JDBC-sqlite class
            Class.forName("org.sqlite.JDBC");

            // connect to sqlite database at specified location
            // if doesn't exist then new database will be created
            this.dbConnection = DriverManager.getConnection("jdbc:sqlite:" + this.filePath);

            // enable foreign keys
            Statement setupStatement = this.dbConnection.createStatement();
            setupStatement.execute("PRAGMA foreign_keys=ON;");
            setupStatement.close();

            // disable auto commit (enables user defined transactions)
            this.dbConnection.setAutoCommit(false);

            // construct SQL Engine and Point Engine
            this.dbEngine = new SQLEngine();
            this.pointEngine = new PointEngine(pointFilePath);

            // reserve space for the number of incoming points
            if(numPoints != null)
                this.pointEngine.reserveSpace(numPoints);

        }
        catch(Exception e)
        {
            LOGGER.log(Level.WARNING, "Unable to initialize database connection at " + this.filePath, e);

            // SQLExceptions contain leading tags that describe what ACTUALLY happened
            // looking for case when the file chosen was not a database
            if(e.getMessage().contains("[SQLITE_NOTADB]"))
                throw new DataFormatException(e.getMessage());

            else throw e;
        }
    }

    //**********************************************//
    //                 LOAD MODEL                   //
    //**********************************************//

    @Override
    public MzTreeNode loadRootNode() throws Exception {
        MzTreeNode rootNode = new MzTreeNode();

        // query for and retrieve root node
        try(ResultSet rootNodeResult = this.dbConnection.createStatement().executeQuery(this.dbEngine.selectRootNodeStatement)) {
            rootNodeResult.next();

            this.assignNodeValues(rootNode, rootNodeResult);
        }
        return rootNode;
    }

    @Override
    public List<MzTreeNode> loadChildNodes(MzTreeNode parent) throws Exception {
        return this.dbEngine.selectNode(parent.nodeID, true);
    }

    /**
     * Reads all traces from the database and inserts them into the traceMap
     * @param mzTree
     * @throws SQLException
     */
    @Override
    public Map<Integer, Integer> loadTraceMap() throws Exception
    {
        return dbEngine.loadTraceMap();
    }

    //**********************************************//
    //                  SAVE POINTS                 //
    //**********************************************//

    @Override
    public void savePoints(SavePointsTask task, ImportState importState) throws IOException
    {

        // inform the MzTreeNode of its position in the file and number of points
        task.node.numSavedPoints = task.dataset.size();

        // iterate through all points
        for(MsDataPoint point : task.dataset)
        {
            // insert point into database
            this.pointEngine.insert(point);

            // a point is a single unit of work
            this.workDone++;

            importState.setWorkDone(this.workDone);
        }
        task.node.fileIndex = (long)task.dataset.get(0).pointID * MsDataPoint.DISK_NUM_BYTES_PER_POINT;
    }

    @Override
    public Integer getPointCount() throws Exception {
        return this.pointEngine.pointCount;
    }

    @Override
    public int[] getNodePointIDs(int nodeID) throws Exception {
        return this.dbEngine.selectPointIDsByNode(nodeID);
    }

    /**
     * Assigns to the node the values at the current result set cursor position
     * @param node MzTreeNode to assign values to
     * @param rs ResultSet containing Node results, expects cursor to be in correct position
     * @throws SQLException
     */
    private void assignNodeValues(MzTreeNode node, ResultSet rs) throws SQLException {
        node.nodeID = rs.getInt(1);

        // the jdbc way to determine nulls. good stuff
        node.fileIndex = rs.getLong(2);
        if (rs.wasNull())
            node.fileIndex = null;

        node.numSavedPoints = rs.getInt(3);
        if (rs.wasNull())
            node.numSavedPoints = null;

        node.mzMin = rs.getDouble(4);
        node.mzMax = rs.getDouble(5);
        node.rtMin = rs.getFloat(6);
        node.rtMax = rs.getFloat(7);
        node.intMin = rs.getDouble(8);
        node.intMax = rs.getDouble(9);
    }

    //**********************************************//
    //               UPDATE TRACES                  //
    //**********************************************//

    @Override
    public void updateTraces(int traceID, Integer[] targets) throws SQLException, IOException
    {
        // iterate through all pointID/traceID pairs
        for (Integer pointID : targets) {
            this.pointEngine.updatePointTrace(pointID, traceID);
        }

        this.dbConnection.commit();
    }

    @Override
    public void updateTrace(int traceID, double centroidMZ, float minRT, float maxRT, double intensitySum) throws SQLException
    {
        this.dbEngine.updateTrace(traceID, centroidMZ, minRT, maxRT, intensitySum);
    }

    @Override
    public void insertTrace(int traceID, int envelopeID) throws SQLException
    {
        this.dbEngine.insertTrace(traceID, envelopeID);

        this.dbConnection.commit();
    }

    //**********************************************//
    //                LOAD TRACES                   //
    //**********************************************//

    @Override
    public List<IsotopeTrace> loadTraces(boolean single) throws SQLException
    {
        return this.dbEngine.loadTraces(single);
    }

    /**
     * Converts a resultSet currently pointing to an IsotopeTrace entry to an IsotopeTrace object
     * @param resultSet database resultset pointing at IT
     * @return IsotopeTrace object corresponding to result set contents
     * @throws SQLException
     */
    private IsotopeTrace resultSetToIsotopeTrace(ResultSet resultSet) throws SQLException
    {
        int traceID = resultSet.getInt(1);

        Double centroidMZ = resultSet.getDouble(3);
        centroidMZ = resultSet.wasNull() ? null : centroidMZ;

        Float minRT = resultSet.getFloat(4);
        minRT = resultSet.wasNull() ? null : minRT;

        Float maxRT = resultSet.getFloat(5);
        maxRT = resultSet.wasNull() ? null : maxRT;

        Double intensitySum = resultSet.getDouble(6);
        intensitySum = resultSet.wasNull() ? null : intensitySum;

        return new IsotopeTrace(traceID, centroidMZ, minRT, maxRT, intensitySum);
    }

    //**********************************************//
    //               DELETE TRACE                  //
    //**********************************************//

    @Override
    public void deleteTrace(int traceID) throws SQLException, IOException
    {
        // update point's trace
        this.dbEngine.deleteTrace(traceID);

        this.dbConnection.commit();
    }
    //**********************************************//
    //               DELETE TRACEs                  //
    //**********************************************//

    @Override
    public void deleteTraces() throws SQLException, IOException
    {
        // update point's trace
        this.dbEngine.deleteTraces();

        this.dbConnection.commit();
    }

    //**********************************************//
    //               INSERT ENVELOPES               //
    //**********************************************//

    @Override
    public void insertEnvelope(IsotopicEnvelope envelope) throws SQLException
    {
        this.dbEngine.insertOrIgnoreEnvelope(envelope.envelopeID, envelope.chargeState, envelope.monoisotopicMZ, envelope.intensitySum, envelope.relativeIntensities);

        this.dbConnection.commit();
    }

    //**********************************************//
    //               DELETE ENVELOPES               //
    //**********************************************//

    @Override
    public void deleteEnvelopes(int[] envelopeIDs) throws SQLException
    {
        this.dbEngine.deleteEnvelopes(envelopeIDs);

        this.dbConnection.commit();
    }

    //**********************************************//
    //               UPDATE ENVELOPES               //
    //**********************************************//

    @Override
    public void updateEnvelopes(int envelopeID, Integer[] targets) throws SQLException
    {
        // iterate through all traceID,envelopeID pairs
        for (Integer traceID : targets) {
            this.dbEngine.updateTrace(traceID, envelopeID);
        }

        this.dbConnection.commit();
    }

    //**********************************************//
    //                  SAVE NODE                   //
    //**********************************************//

    @Override
    public int saveNode(MzTreeNode childNode, int parentNodeID) throws SQLException
    {
        return this.dbEngine.insert(childNode, parentNodeID);
    }

    //**********************************************//
    //               SAVE NODE POINTS               //
    //**********************************************//

    @Override
    public void saveNodePoints(MzTreeNode node, ImportState importState) throws SQLException
    {
        // set the node points array in DB
        this.dbEngine.updateNodePoints(node.nodeID, node.pointIDs);

        // node points are not significant work done
    }

    //**********************************************//
    //                  LOAD POINTS                 //
    //**********************************************//

    @Override
    public List<MsDataPoint> loadPoints(List<Integer> pointIDs) throws IOException
    {
        return this.pointEngine.selectPoints(pointIDs);
    }

    @Override
    public List<MsDataPoint> loadLeavesPointsInBounds(List<MzTreeNode> leaves, double mzmin, double mzmax, float rtmin, float rtmax) throws IOException
    {
        List<MsDataPoint> results = new ArrayList<>();

        for(MzTreeNode leaf : leaves)
        {
            results.addAll(this.pointEngine.selectLeafPointsInBounds(leaf, mzmin, mzmax, rtmin, rtmax));
        }

        return results;
    }

    //**********************************************//
    //                    FLUSH                     //
    //**********************************************//

    @Override
    public void flush() throws SQLException, IOException
    {
        this.dbConnection.commit();
        this.pointEngine.flush();
    }

    //**********************************************//
    //                 GET FILE PATH                //
    //**********************************************//

    @Override
    public String getFilePath()
    {
        return this.filePath;
    }

    //**********************************************//
    //                    CLOSE                     //
    //**********************************************//

    @Override
    public void close()
    {
        try {
            this.flush();
            this.dbConnection.close();
            this.pointEngine.pointFile.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not cleanly close storage", e);
        } finally {
            this.dbConnection = null;
            this.pointEngine = null;
        }
    }

    @Override
    public void copy(Path targetFilepath) throws IOException {
        Path targetPointFilepath = Paths.get(targetFilepath.toString() + "-points");
        Files.copy(Paths.get(filePath), targetFilepath);
        Files.copy(Paths.get(pointFilePath), targetPointFilepath);
    }

    //**********************************************//
    //                 SQL ENGINE                   //
    //**********************************************//

    /**
     * Inner class for preparing and executing SQL statements
     */
    private class SQLEngine{

        private static final int APPLICATION_ID = 223764262;
        private static final int USER_VERSION = 5;

        // SQL statement for retrieiving root node
        public final String selectRootNodeStatement = "SELECT nodeId, fileIndex, numPoints, mzMin, mzMax, rtMin, rtMax, intMin, intMax, parentId, points FROM Node WHERE parentId IS NULL;";

        // ordered create table statements
        public final String[] orderedCreateTableStatements = {
            "CREATE TABLE IF NOT EXISTS Node (nodeId INTEGER PRIMARY KEY, fileIndex INTEGER, numPoints INTEGER, mzMin DOUBLE NOT NULL, mzMax DOUBLE NOT NULL, rtMin FLOAT NOT NULL, rtMax FLOAT NOT NULL, intMin DOUBLE, intMax DOUBLE, parentId INTEGER, points BLOB, FOREIGN KEY(parentId) REFERENCES Node(nodeId));",
            "CREATE INDEX IF NOT EXISTS Node_parentId ON Node (parentId);",
            "CREATE TABLE IF NOT EXISTS Trace (traceId INTEGER PRIMARY KEY, envelopeID INTEGER, centroidMZ DOUBLE, minRT FLOAT, maxRT FLOAT, intensitySum DOUBLE, FOREIGN KEY(envelopeID) REFERENCES Envelope(envelopeId));",
            "CREATE INDEX IF NOT EXISTS Trace_envelopeID ON Trace(envelopeID);",
            "CREATE TABLE IF NOT EXISTS Envelope (envelopeId INTEGER PRIMARY KEY, chargeState INTEGER, monoisotopicMZ DOUBLE, intensitySum DOUBLE, relativeIntensities TEXT);",
        };

        // insert statements
        private final PreparedStatement insertNodeStatement;
        private final PreparedStatement insertTraceStatement;
        private PreparedStatement insertOrIgnoreEnvelopeStatement;

        // select statements
        private final String selectPointIDsByNodeSQL = "SELECT points FROM Node WHERE nodeId=?;";
        private final String selectNodeByParentSQL = "SELECT nodeId, fileIndex, numPoints, mzMin, mzMax, rtMin, rtMax, intMin, intMax, parentId, points FROM Node WHERE parentId=?;";
        private final String selectNodeByIdSQL = "SELECT nodeId, fileIndex, numPoints, mzMin, mzMax, rtMin, rtMax, intMin, intMax, parentId, points FROM Node WHERE nodeId=?;";
        public final String selectAllTracesSQL = "SELECT traceId, envelopeID, centroidMZ, minRT, maxRT, intensitySum FROM Trace;";
        public final String selectSingleTraceSQL = "SELECT traceId, envelopeID, centroidMZ, minRT, maxRT, intensitySum FROM Trace LIMIT 1;";


        // update statements
        private final PreparedStatement setEnvelopeStatement;
        private final PreparedStatement updateNodePointsStatement;
        private final PreparedStatement setCompiledTraceStatement;

        // delete statements
        private final PreparedStatement deleteTraceStatement;
        private final PreparedStatement deleteEnvelopeStatement;


        /**
         * Default constructor
         * Ensures that tables exist within database and creates prepared statements
         */
        public SQLEngine() throws Exception
        {
            int appId;

            // check the application ID
            try (Statement checkAppIdStatement = dbConnection.createStatement()) {
                ResultSet appIdResult = checkAppIdStatement.executeQuery("PRAGMA application_id;");
                appIdResult.next();
                appId = appIdResult.getInt(1);
            }

            if (appId == 0) {
                // appId == 0 means it's not an mzTree or it's empty

                try (PreparedStatement checkEmpty = dbConnection.prepareStatement("SELECT count(*) FROM sqlite_master;")) {
                    ResultSet ers = checkEmpty.executeQuery();
                    ers.next();
                    int tables = ers.getInt(1);
                    if (tables != 0) {
                        throw new Exception("Not an mzTree file");
                    }
                }

                LOGGER.log(Level.INFO, "Creating a new mzTree file, version " + USER_VERSION);

                // initializing a new database with the current version
                try (Statement updateAppIdStatement = dbConnection.createStatement()) {
                    updateAppIdStatement.execute("PRAGMA application_id = " + APPLICATION_ID + ";");
                    updateAppIdStatement.execute("PRAGMA user_version = " + USER_VERSION + ";");
                }

                Statement statement = dbConnection.createStatement();
                for(String createTableStatement : this.orderedCreateTableStatements)
                    statement.execute(createTableStatement);
                statement.close();
                dbConnection.commit();

            } else if (appId != APPLICATION_ID) {
                throw new SQLException("Not an mzTree file.");
            }

            int userVersion;
            // check the user version for upgrades
            try (Statement userVersionStatement = dbConnection.createStatement()) {
                ResultSet userVersionResult = userVersionStatement.executeQuery("PRAGMA user_version;");
                userVersionResult.next();
                userVersion = userVersionResult.getInt(1);
            }

            // process version upgrades
            if (userVersion != USER_VERSION)
            {
                LOGGER.log(Level.INFO, "Converting mzTree file from version " + userVersion);

                // use switch fall-through (no "break" statement) to run multiple migrations
                // commented-out examples below
                switch(userVersion) {
                    case 5:
                        //convert_v5_v6();
                    case 6:
                        //convert_v6_v7();
                        break;
                    default:
                        throw new SQLException("Unsupported mzTree file version.");
                }

                try(Statement updateUserVersionStatement = dbConnection.createStatement()) {
                    updateUserVersionStatement.execute("PRAGMA user_version = " + USER_VERSION + ";");
                }

                LOGGER.log(Level.INFO, "mzTree file converted to version " + USER_VERSION);
            }

            // init insert statements
            this.insertNodeStatement = dbConnection.prepareStatement("INSERT INTO Node (nodeId, fileIndex, numPoints, mzMin, mzMax, rtMin, rtMax, intMin, intMax, parentId, points) VALUES (?,?,?,?,?,?,?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            this.insertTraceStatement = dbConnection.prepareStatement("INSERT INTO Trace (traceId, envelopeID, centroidMZ, minRT, maxRT, intensitySum) VALUES (?,?,?,?,?,?);");
            this.insertOrIgnoreEnvelopeStatement = dbConnection.prepareStatement("INSERT OR IGNORE INTO Envelope (envelopeId, chargeState, monoisotopicMZ, intensitySum, relativeIntensities) VALUES (?,?,?,?,?);");

            // init update statements
            this.setEnvelopeStatement = dbConnection.prepareStatement("UPDATE Trace SET envelopeId=? WHERE traceId=?;");
            this.updateNodePointsStatement = dbConnection.prepareStatement("UPDATE Node SET points=? WHERE nodeId=?");
            this.setCompiledTraceStatement = dbConnection.prepareStatement("UPDATE Trace SET centroidMZ=?, minRT=?, maxRT=?, intensitySum=? WHERE traceId=?");

            // init delete statements
            this.deleteTraceStatement = dbConnection.prepareStatement("DELETE FROM Trace WHERE traceId=?;");
            this.deleteEnvelopeStatement = dbConnection.prepareStatement("DELETE FROM Envelope WHERE envelopeId=?;");
        }

        /**
         * Inserts an MzTreeNode into the database
         * @param node MzTreeNode to insert
         * @param parentNodeID node's parentNodeID, 0 signals null parentNodeID (root node only)
         * @return ID of the node in the database
         * @throws SQLException
         */
        public int insert(MzTreeNode node, int parentNodeID) throws SQLException
        {
            // set values in prepared statement

            // set null for primary key, db autoincrements
            this.insertNodeStatement.setNull(1, Types.INTEGER);

            // file index
            if(node.fileIndex != null)
                this.insertNodeStatement.setLong(2, node.fileIndex);
            else
                this.insertNodeStatement.setNull(2,Types.BIGINT);

            // num points in file
            if(node.numSavedPoints != null)
                this.insertNodeStatement.setInt(3, node.numSavedPoints);
            else
                this.insertNodeStatement.setNull(3, Types.INTEGER);

            // mz, rt and intensity bounds
            this.insertNodeStatement.setDouble(4, node.mzMin);
            this.insertNodeStatement.setDouble(5, node.mzMax);
            this.insertNodeStatement.setDouble(6, node.rtMin);
            this.insertNodeStatement.setDouble(7, node.rtMax);
            this.insertNodeStatement.setDouble(8, node.intMin);
            this.insertNodeStatement.setDouble(9, node.intMax);

            // set parent node ID, 0 implies null parent node ID
            if (parentNodeID != 0)
                this.insertNodeStatement.setInt(10, parentNodeID);
            else
                this.insertNodeStatement.setNull(10, Types.SMALLINT);

            this.insertNodeStatement.setNull(11, Types.BLOB);

            // execute insert
            this.insertNodeStatement.executeUpdate();

            // retrieve generated key and return
            ResultSet results = this.insertNodeStatement.getGeneratedKeys();
            results.next();
            return results.getInt(1);
        }

        /**
         * Creates a trace entity in the database
         * @param traceID ID of the trace entity (given by the client)
         * @param envelopeID trace's envelope ID (given by the client)
         *      0 implies null envelope
         * @throws SQLException
         */
        public void insertTrace(int traceID, Integer envelopeID) throws SQLException
        {
            // set traceID
            this.insertTraceStatement.setInt(1, traceID);

            // set envelopeID, 0 implies null envelopeID
            if(envelopeID != null)
            {
                this.insertOrIgnoreEnvelope(envelopeID, null, null, null, null);
                this.insertTraceStatement.setInt(2, envelopeID);
            }
            else
            {
                this.insertTraceStatement.setNull(2,Types.SMALLINT);
            }

            // compiled trace atrributes, unknown on creation
            this.insertTraceStatement.setNull(3, Types.DOUBLE); //centroidMZ: double
            this.insertTraceStatement.setNull(4, Types.FLOAT); // startRT: float
            this.insertTraceStatement.setNull(5, Types.FLOAT); // endRT: float
            this.insertTraceStatement.setNull(6, Types.DOUBLE); //intensitySum: double

            // execute insert
            this.insertTraceStatement.executeUpdate();
        }

        /**
         * Inserts the envelope into the database if the envelopeID doesn't already exist
         * @param envelopeID
         * @param chargeState
         * @param monoisotopicMZ
         * @param intensitySum
         * @param relativeIntensities
         * @throws SQLException
         */
        public void insertOrIgnoreEnvelope(int envelopeID, Integer chargeState, Double monoisotopicMZ, Double intensitySum, double[] relativeIntensities) throws SQLException
        {
            this.insertOrIgnoreEnvelopeStatement.setInt(1, envelopeID);
            if(chargeState != null)
                this.insertOrIgnoreEnvelopeStatement.setInt(2, chargeState);
            else
                this.insertOrIgnoreEnvelopeStatement.setNull(2, Types.INTEGER);

            if(monoisotopicMZ != null)
                this.insertOrIgnoreEnvelopeStatement.setDouble(3, monoisotopicMZ);
            else
                this.insertOrIgnoreEnvelopeStatement.setNull(3, Types.DOUBLE);

            if(intensitySum != null)
                this.insertOrIgnoreEnvelopeStatement.setDouble(4, intensitySum);
            else
                this.insertOrIgnoreEnvelopeStatement.setNull(4, Types.DOUBLE);

            if(relativeIntensities != null)
                this.insertOrIgnoreEnvelopeStatement.setString(5, String.join(",", this.relativeIntensitiesString(relativeIntensities)));
            else
                this.insertOrIgnoreEnvelopeStatement.setNull(5, Types.VARCHAR);

            this.insertOrIgnoreEnvelopeStatement.executeUpdate();
        }

        /**
         * Converts an array of doubles into a string of comma separated values
         * @param relInts array of doubles
         * @return string of comma separated values corresponding to relInts
         */
        private String relativeIntensitiesString(double[] relInts)
        {
            StringBuilder result = new StringBuilder();
            Arrays.stream(relInts).forEach(relInt -> result.append(relInt));
            return result.toString();
        }

        /**
         * Inserts a nodepoint relationship entity into the database
         * @param nodeID ID of the node in the nodepoint relationship
         * @param pointID ID of the point in the nodepoint relationship
         * @throws SQLException
         */
        public void updateNodePoints(int nodeID, int[] pointIDs) throws SQLException
        {
            // convert pointIDs into a byte array
            ByteBuffer bytes = ByteBuffer.allocate(pointIDs.length * 4).order(ByteOrder.BIG_ENDIAN);
            IntBuffer wrapper = bytes.asIntBuffer();
            wrapper.put(pointIDs);
            bytes.rewind();

            // update the Node.points field in the database
            this.updateNodePointsStatement.setBytes(1, bytes.array());
            this.updateNodePointsStatement.setInt(2, nodeID);
            this.updateNodePointsStatement.executeUpdate();
        }

        /**
         * Performs a query on the NODE table, querying for a single node by ID
         * or for a collection of nodes by parentIDs
         * @param id ID to place in the WHERE clause
         * @param byParent if true selects by the parentId column,
         *  else selects by the primary key nodeId
         * @return Node(s) retrieved by query
         */
        public ArrayList<MzTreeNode> selectNode(int id, boolean byParent) throws SQLException
        {
            ResultSet results;
            PreparedStatement selectNodePrepStatement;

            if(byParent)
            {
                selectNodePrepStatement = dbConnection.prepareStatement(this.selectNodeByParentSQL);
                selectNodePrepStatement.setInt(1, id);
                results = selectNodePrepStatement.executeQuery();
            }
            else
            {
                selectNodePrepStatement = dbConnection.prepareStatement(this.selectNodeByIdSQL);
                selectNodePrepStatement.setInt(1,id);
                results = selectNodePrepStatement.executeQuery();
            }

            // flush result set to list of child nodes
            ArrayList<MzTreeNode> nodes = new ArrayList<>();
            while(results.next())
            {
                // create new node and assign values
                MzTreeNode childNode = new MzTreeNode();
                HybridStorage.this.assignNodeValues(childNode,results);

                // collect node
                nodes.add(childNode);
            }

            // close prepared statement (also closes resultset)
            selectNodePrepStatement.close();

            return nodes;
        }

        /**
         * Queries for pointIDs of points belonging to the node specified by nodeID
         * @param nodeID node whose points' IDs are to be collected
         * @return List of IDs of points belonging to the specified node
         */
        public int[] selectPointIDsByNode(int nodeID) throws SQLException
        {
            try(PreparedStatement selectPointIDsByNodeStatement = dbConnection.prepareStatement(this.selectPointIDsByNodeSQL))
            {
                // query for pointIDs by nodeID
                selectPointIDsByNodeStatement.setInt(1, nodeID);
                ResultSet results = selectPointIDsByNodeStatement.executeQuery();

                // transform resultset into list of pointIDs
                ArrayList<Integer> pointIDs = new ArrayList<>();
                if(results.next()) {
                    // convert the byte array to an integer array
                    byte[] bytes = results.getBytes(1);
                    int[] ints = new int[bytes.length / 4];
                    ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(ints);

                    // add the IDs to the result list
                    pointIDs.ensureCapacity(ints.length);
                    for (int i = 0; i < ints.length; i++) {
                        pointIDs.add(ints[i]);
                    }
                }

                return pointIDs.stream().mapToInt(x -> x).toArray();
            }
        }


        /**
         * Loads the Trace table into a Map (traceID -> envelopeID) and returns the Map
         * @return Trace table as a traceID->envelopeID Map<Integer, Integer>
         * @throws SQLException
         */
        public Map<Integer,Integer> loadTraceMap() throws SQLException
        {
            try(PreparedStatement selectAllTracesPrepStatement = dbConnection.prepareStatement(this.selectAllTracesSQL))
            {

                Map<Integer,Integer> traceMap = HashIntIntMaps.newMutableMap();

                // query for all traces
                ResultSet traces = selectAllTracesPrepStatement.executeQuery();

                // iterate through all results collecting trace entities into trace map
                while(traces.next())
                {
                    int traceID = traces.getInt(1);
                    int envelopeID = traces.getInt(2);
                    traceMap.put(traceID, envelopeID);
                }

                return traceMap;
            }
        }

        /**
         * Updates a trace to have the given envelopeID (could be null)
         * @param traceID ID of the trace to be updated
         * @param envelopeID new envelopeID value (can be null)
         */
        public void updateTrace(int traceID, Integer envelopeID) throws SQLException
        {
            // set envelopeID value (null allowed)
            if(envelopeID == null)
                this.setEnvelopeStatement.setNull(1, Types.SMALLINT);
            else
            {
                this.insertOrIgnoreEnvelope(envelopeID, null, null, null, null);
                this.setEnvelopeStatement.setInt(1, envelopeID);
            }

            // set traceID value
            this.setEnvelopeStatement.setInt(2, traceID);

            // execute update
            this.setEnvelopeStatement.executeUpdate();
        }

        /**
         * Updates the trace to have the given compiled trace attributes
         * @param centroidMZ
         * @param startRT
         * @param endRT
         * @param intensitySum
         * @throws SQLException
         */
        public void updateTrace(int traceID, double centroidMZ, float startRT, float endRT, double intensitySum) throws SQLException
        {
            // set compiled trace attribute values
            this.setCompiledTraceStatement.setDouble(1, centroidMZ);
            this.setCompiledTraceStatement.setFloat(2, startRT);
            this.setCompiledTraceStatement.setFloat(3, endRT);
            this.setCompiledTraceStatement.setDouble(4, intensitySum);

            // set trace ID
            this.setCompiledTraceStatement.setInt(5, traceID);

            // execute update statement
            this.setCompiledTraceStatement.executeUpdate();
        }

        /**
         * Deletes the specified trace from the database, first taking the time
         * update any points that reference the trace to have a null trace reference
         * @param traceID ID of the trace to delete
         * @throws SQLException
         */
        public void deleteTrace(int traceID) throws SQLException, IOException
        {
            pointEngine.clearTrace(traceID);

            // now that references are cleared, delete the trace
            this.deleteTraceStatement.setInt(1,traceID);

            // delete that sucka
            this.deleteTraceStatement.executeUpdate();
        }

        /**
         * loads a trace or all traces from the database
         * @param single if true returns a single trace (for content testing) result set,
         * otherwise returns all traces in a result set
         * @return result set containing a single trace or all traces
         */
        public List<IsotopeTrace> loadTraces(boolean single) throws SQLException
        {
            List<IsotopeTrace> results = new ArrayList<>();
            if(single)
            {
                try(Statement selectSingleTrace = dbConnection.createStatement())
                {
                    ResultSet rs = selectSingleTrace.executeQuery(this.selectSingleTraceSQL);
                    if (rs.next())
                        results.add(resultSetToIsotopeTrace(rs));
                }
            }
            else
            {
                try(Statement selectTraces = dbConnection.createStatement())
                {
                    ResultSet rs = selectTraces.executeQuery(this.selectAllTracesSQL);
                    while(rs.next())
                        results.add(resultSetToIsotopeTrace(rs));
                }
            }
            return results;
        }

        /**
         * Deletes all traces from the database, first taking the time
         * update any points that reference the trace to have a null trace reference
         * @param traceID ID of the trace to delete
         * @throws SQLException
         */
        public void deleteTraces() throws SQLException, IOException
        {
          try(Statement stmt = dbConnection.createStatement())
          {

              // clear references
              pointEngine.clearTraces();

              // delete all traces
              stmt.execute("DELETE FROM Trace;");
          }
        }

        /**
         * Deletes the specified envelopes from the database. If passed a null pointer
         * deletes all envelopes.
         * @param envelopeIDs envelopes to delete (null -> all envelopes)
         * @throws SQLException
         */
        private void deleteEnvelopes(int[] envelopeIDs) throws SQLException
        {
            // delete all envelopes!
            if(envelopeIDs == null)
            {
                try(Statement stmt = dbConnection.createStatement())
                {
                    // clear references
                    stmt.execute("UPDATE Trace SET envelopeID=NULL;");

                    // delete all envelopes
                    stmt.execute("DELETE FROM Envelope;");
                }
            }
            // delete the specified envelopes
            else
            {
                try (PreparedStatement nullifyEnvelopeIdStatement = dbConnection.prepareStatement("UPDATE Trace SET envelopeID=NULL WHERE envelopeID=?")) {
                    for(int envelopeID : envelopeIDs)
                    {
                        nullifyEnvelopeIdStatement.setInt(1, envelopeID);
                        nullifyEnvelopeIdStatement.executeUpdate();
                        this.deleteEnvelopeStatement.setInt(1, envelopeID);
                        this.deleteEnvelopeStatement.execute();
                    }
                }
            }
        }
    }

    //**********************************************//
    //                POINT ENGINE                  //
    //**********************************************//
    private static class PointEngine {

        /* POINT FORMAT
         * NOTE: Java uses big-endian in RandomAccessFile and in ByteBuffer
         *
         * MZ   : 8 [DOUBLE]
         * RT   : 4 [FLOAT]
         * INTEN: 8 [DOUBLE]
         * TRACE: 4 [INTEGER]
         */

        private final RandomAccessFile pointFile;

        // location within a point entry where the trace is located
        private static final int TRACE_OFFSET = 8 + 4 + 8;

        // number of points in the file
        private int pointCount;

        /**
         * Creates or opens the point storage file
         * @throws IOException
         */
        public PointEngine(String pointFilePath) throws IOException {
            pointFile = new RandomAccessFile(pointFilePath, "rw");
            this.pointCount = 0;

            if (pointFile.length() > 0) {
                // existing file
                pointCount = (int) (pointFile.length() / MsDataPoint.DISK_NUM_BYTES_PER_POINT);
            }

        }

        /* Converts a byte array to point data */
        private MsDataPoint pointFromBytes(int id, byte[] data)
        {
            ByteBuffer buf = ByteBuffer.wrap(data);
            double mz = buf.getDouble();
            float rt = buf.getFloat();
            double intensity = buf.getDouble();
            int traceID = buf.getInt();

            MsDataPoint pt = new MsDataPoint(id, mz, rt, intensity);
            pt.traceID = traceID;
            return pt;
        }

        /* Converts point data to a byte array */
        private byte[] pointToBytes(MsDataPoint point) {
            ByteBuffer buf = ByteBuffer.allocate(MsDataPoint.DISK_NUM_BYTES_PER_POINT);
            buf.putDouble(point.mz);
            buf.putFloat(point.rt);
            buf.putDouble(point.intensity);
            buf.putInt(point.traceID);
            return buf.array();
        }

        /* Reserves space in the file for the necessary number of points */
        public synchronized void reserveSpace(int numPoints) throws IOException {
            pointFile.setLength((long)(numPoints) * (long)MsDataPoint.DISK_NUM_BYTES_PER_POINT);
        }

        /**
         * Inserts a MsDataPoint into the point file at the current point file pointer location
         * @param point MsDataPoint to insert into database
         * @throws SQLException
         */
        public synchronized void insert(MsDataPoint point) throws IOException
        {
            // assign the point's ID
            point.pointID = this.pointCount;
            this.pointCount++;

            // write the point to the point file
            pointFile.seek((long)point.pointID * (long)MsDataPoint.DISK_NUM_BYTES_PER_POINT);
            byte[] data = pointToBytes(point);
            pointFile.write(data);
        }

         /**
         * Selects a point entity from the database, returns as MsDataPoint object
         * @param pointID ID of point to select
         * @return MsDataPoint selected from database
         * @throws SQLException
         */
        public synchronized MsDataPoint selectPoint(int pointID) throws IOException
        {
            if (pointID < 0) {
                throw new IndexOutOfBoundsException("pointID");
            }

            if (pointID >= this.pointCount) {
                throw new IndexOutOfBoundsException("pointID");
            }

            // convert to long to avoid integer overflow
            long pointLocation = (long)pointID * (long)MsDataPoint.DISK_NUM_BYTES_PER_POINT;
            this.pointFile.seek(pointLocation);
            byte[] data = new byte[MsDataPoint.DISK_NUM_BYTES_PER_POINT];
            this.pointFile.read(data);
            return pointFromBytes(pointID, data);
        }

        /**
         * Queries for points specified in pointIDs
         * @param pointIDs IDs of points to select
         * @return MsDataPoints selected from storage
         * @throws SQLException
         */
        public synchronized List<MsDataPoint> selectPoints(List<Integer> pointIDs) throws IOException
        {
            // return list
            ArrayList<MsDataPoint> points = new ArrayList<>(pointIDs.size());
            for (Integer id : pointIDs) {
                points.add(this.selectPoint(id));
            }

            return points;
        }

        /**
         * Selects a leaf node's points from the point file by loading its entire block of points.
         * Allows for accessing an entire leaf node's data points with only one file seek.
         * Additionally trims the resulting list according to the given bounds
         * @param leaf leaf node that will have data block loaded
         * @param mzmin lower mz bound
         * @param mzmax upper mz bound
         * @param rtmin lower rt bound
         * @param rtmax upper rt bound
         * @return list of MsDataPoints loaded from leaf node's data block, trimmed according to data bounds
         * @throws IOException
         */
        public synchronized List<MsDataPoint> selectLeafPointsInBounds(MzTreeNode leaf, double mzmin, double mzmax, float rtmin, float rtmax) throws IOException
        {
            // results list
            List<MsDataPoint> results = new ArrayList<>();

            if (leaf.fileIndex == null) {
                // not a leaf node, or upgraded from a version without this optimization
                return selectPoints(Arrays.stream(leaf.pointIDs).boxed().collect(Collectors.toList()))
                        .stream().filter(p -> p.isInBounds(mzmin, mzmax, rtmin, rtmax)).collect(Collectors.toList());
            }

            // start location of node in point file
            long nodeStartLocation = leaf.fileIndex;

            // seek to start of node in point file
            this.pointFile.seek(nodeStartLocation);

            // allocated space for the node block
            byte[] data = new byte[leaf.numSavedPoints * MsDataPoint.DISK_NUM_BYTES_PER_POINT];

            // read node block
            this.pointFile.read(data);

            // parse points from retrieved binary
            for(int i = 0; i < leaf.numSavedPoints; i++)
            {
                // current point's sub array
                byte[] pointData = Arrays.copyOfRange(data, i * MsDataPoint.DISK_NUM_BYTES_PER_POINT, (i+1) * MsDataPoint.DISK_NUM_BYTES_PER_POINT);

                // parse into MsDataPoint
                MsDataPoint point = this.pointFromBytes(leaf.pointIDs[i], pointData);

                //assert point.mz >= leaf.mzMin && point.mz <= leaf.mzMax && point.rt >= leaf.rtMin && point.rt <= leaf.rtMax : "A loaded point was outside of its node's bounds!!!";

                // include in result set if within bounds
                if(point.isInBounds(mzmin, mzmax, rtmin, rtmax))
                    results.add(point);
            }
            return results;
        }

        /**
         * Updates a point in the DB to have the given dbTraceID
         * @param pointID ID of the point to be updated
         * @param traceID new dbTraceID value. If 0, DB entry will have dbTraceID set to null
         */

        public synchronized void updatePointTrace(int pointID, int traceID) throws IOException
        {
            long pointLocation = (long)pointID * MsDataPoint.DISK_NUM_BYTES_PER_POINT;

            pointFile.seek(pointLocation + TRACE_OFFSET);
            pointFile.writeInt(traceID);
        }

        /**
         * Deletes a trace by setting every reference to it to no-trace
         * @param traceID trace ID to erase
         * @throws IOException
         */
        private synchronized void clearTrace(int traceID) throws IOException {
            for (int i = 0; i < pointCount; i++) {
                pointFile.seek(i * MsDataPoint.DISK_NUM_BYTES_PER_POINT + TRACE_OFFSET);
                if (pointFile.readInt() == traceID) {
                    pointFile.writeInt(0);
                }
            }
        }
        private synchronized void clearTraces() throws IOException {
            for (int i = 0; i < pointCount; i++) {
                pointFile.seek(i * MsDataPoint.DISK_NUM_BYTES_PER_POINT + TRACE_OFFSET);
                pointFile.writeInt(0);
            }
        }

        /* Ensures changes have been saved to the underlying storage medium */
        private synchronized void flush() throws IOException {
            pointFile.getFD().sync();
        }
    }
}
