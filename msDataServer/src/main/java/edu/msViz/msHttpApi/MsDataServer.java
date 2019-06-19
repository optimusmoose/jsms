package edu.msViz.msHttpApi;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import edu.msViz.mzTree.*;
import edu.msViz.xnet.dataTypes.IsotopeTrace;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Http server using Spark web server library
 */
public final class MsDataServer {

    private static final Logger LOGGER = Logger.getLogger(MsDataServer.class.getName());
    
    // path to API root
    private static final String API_ROOT = "/api/v2";

    // MzTree data model
    private MzTree mzTree;
    
    // mutual exclusion lock (fair) for updating/saving
    private final ReentrantLock updateSaveLock = new ReentrantLock(true);

    private CommandStack commandStack;
    
    public int getPort() {
        return spark.port();
    }

    private Service spark;
    private boolean successfulStart = false;

    public MsDataServer() { }

    /**
     * Starts the Spark web server listening on specified port 
     * Defines the http methods of the server
     * @param port Port number to listen on
     * @param frame server launch window's frame
     * @param strategy summarization strategy selected from the gui
     */
    public void startServer(int port) {
        spark = Service.ignite();
        this.successfulStart = true;
        spark.initExceptionHandler((e) -> {
            LOGGER.log(Level.WARNING, "Failed to start Spark server", e);
            this.successfulStart = false;
        });
        spark.port(port);

        if (new File("../jsms").exists()) {
            // development mode: use the local "jsms" folder
            // allows developer to run from an IDE and modify js/html files without needing to rebuild
            spark.externalStaticFileLocation("../jsms");
        } else {
            // production mode: use the files packaged into the jar at build time
            spark.staticFileLocation("/edu/msViz/jsms");
        }

        /*       Initialize Web API endpoints       */
        
        spark.get(API_ROOT + "/getpoints", this::getPoints);

        spark.get(API_ROOT + "/gethighestuntraced", this::getHighestUntraced);
        
        spark.get(API_ROOT + "/gettracemap", this::getTraceMap);
        
        spark.get(API_ROOT + "/getnextids", this::getNextIds);
        
        spark.get(API_ROOT + "/filestatus", this::fileStatus);
        
        spark.post(API_ROOT + "/updatesegmentation", this::updateSegmentation);
        
        spark.post(API_ROOT + "/getenvelopeinfo", this::getEnvelopeInfo);
        
    } // END startServer

    public void setMzTree(MzTree newTree) {
        try {
            updateSaveLock.lock();
            this.mzTree = newTree;
            this.commandStack = new CommandStack();
        }
        finally {
            updateSaveLock.unlock();
        }
    }

    //****************************************************
    //                 WEB API FUNCTIONS                ||
    //***************************************************/
    
    /**
     * Processes a query to the data model for data points
     * 
     * API ENDPOINT: GET /getpoints
     * HTTP GET PARAMETERS: 
     *      mzmin -> double : lower mz query bound
     *      mzmax -> double : upper mz query bound
     *      rtmin -> float : lower rt query bound
     *      rtmax -> float : upper rt query bound
     *      numpoints -> int : number of points to return
     * 
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     */
    private Object getPoints(Request request, Response response){

        // no mzTree assigned or not open yet
        if (mzTree == null || mzTree.getLoadStatus() == ImportState.ImportStatus.NONE) {
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No file has been selected.";
        }

        switch (mzTree.getLoadStatus()) {

            // "loading" status types
            case PARSING:
            case CONVERTING:
            case LOADING_MZTREE:
                response.status(HttpServletResponse.SC_CONFLICT);
                return "The server is selecting or processing a file.";

            // "error" status types
            case ERROR:
                response.status(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return "There was a problem opening the file.";

            case READY:
                // get request parameters (query bounds)
                double mzmin, mzmax;
                float rtmin, rtmax;
                int numPoints;
                try{
                    // parse paramaters from request url
                    mzmin = Double.parseDouble(request.queryParams("mzmin"));
                    mzmax = Double.parseDouble(request.queryParams("mzmax"));
                    rtmin = Float.parseFloat(request.queryParams("rtmin"));
                    rtmax = Float.parseFloat(request.queryParams("rtmax"));
                    numPoints = Integer.parseInt(request.queryParams("numpoints"));

                    // numPoints == 0 means no limit
                    numPoints = numPoints == 0 ? Integer.MAX_VALUE : numPoints;
                }
                // catch cases where parameter not included
                catch (NullPointerException ex)
                {
                    response.status(HttpServletResponse.SC_BAD_REQUEST);
                    return "One or more URL parameters missing.";
                }

                // ensure a valid range
                if(mzmax < mzmin || rtmax < rtmin)
                {
                    response.status(HttpServletResponse.SC_BAD_REQUEST);
                    return "Invalid data range requested.";
                }

                // query the mzTree for points within the bounds
                //long start = System.currentTimeMillis();

                List<MsDataPoint> queryResults = mzTree.query(mzmin, mzmax, rtmin, rtmax, numPoints);

                // serialize query results as JSON

                StringBuilder queryResultsJSON = JSONify(queryResults,numPoints,true);

                // respond with HTTP 200 OK
                response.status(HttpServletResponse.SC_OK);

                // format points and traceMap as single JSON object
                return queryResultsJSON.toString();

            // unknown status type
            default:
                response.status(HttpServletResponse.SC_NO_CONTENT);
                return "";
        }
    } // END getPoints

    /**
     * Processes a query to the data model for the highest intensity untraced data point
     *
     * API ENDPOINT: GET /gethighestuntraced
     * HTTP GET PARAMETERS: NONE
     *
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     */
    private Object getHighestUntraced(Request request, Response response){
        // no mzTree assigned or not open yet
        if (mzTree == null || mzTree.getLoadStatus() == ImportState.ImportStatus.NONE) {
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No file has been selected.";
        }

        switch (mzTree.getLoadStatus()) {

            // "loading" status types
            case PARSING:
            case CONVERTING:
            case LOADING_MZTREE:
                response.status(HttpServletResponse.SC_CONFLICT);
                return "The server is selecting or processing a file.";

            // "error" status types
            case ERROR:
                response.status(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return "There was a problem opening the file.";

            case READY:

                if (mzTree.intensityTracker == null) {
                    return "null";
                }

                // get point
                MsDataPoint p = mzTree.intensityTracker.getHighestUntraced();
                String result;
                if (p == null) {
                    result = "null";
                } else {
                    result = String.format("[%d,%d,%f,%f,%f]", p.pointID, p.traceID, p.mz, p.rt, p.intensity);
                }

                // respond with HTTP 200 OK
                response.status(HttpServletResponse.SC_OK);
                return result;

            default:
                response.status(HttpServletResponse.SC_NO_CONTENT);
                return "";
        }
    } // END getHighestUntraced

    /**
     * Processes a query to the data model for data points
     * 
     * API ENDPOINT: GET /gettracemap
     * HTTP GET PARAMETERS: NONE
     * 
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     */
    private Object getTraceMap(Request request, Response response) {
        // no mzTree assigned or not open yet
        if (mzTree == null || mzTree.getLoadStatus() == ImportState.ImportStatus.NONE) {
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No file has been selected.";
        }

        switch (mzTree.getLoadStatus()) {

            // "loading" status types
            case PARSING:
            case CONVERTING:
            case LOADING_MZTREE:
                response.status(HttpServletResponse.SC_CONFLICT);
                return "The server is selecting or processing a file.";

            // "error" status types
            case ERROR:
                response.status(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return "There was a problem opening the file.";

            case READY:

                try {
                    updateSaveLock.lock();

                    // serialize traceMap
                    StringBuilder json = new StringBuilder("{");
                    boolean first = true;
                    for(Map.Entry<Integer,Integer> trace : mzTree.traceMap.entrySet()){
                        Integer val = trace.getValue();
                        if (val == null || val == 0) {
                            continue;
                        }
                        
                        if (!first) {
                            json.append(',');
                        }
                        first = false;
                        json.append("\"").append(trace.getKey()).append("\": ").append(val);
                    }
                    json.append("}");

                    // respond with HTTP 200 OK
                    response.status(HttpServletResponse.SC_OK);
                    return json.toString();
                } finally {
                    updateSaveLock.unlock();
                }

            default:

                // default to HTTP 204 NO_CONTENT
                response.status(HttpServletResponse.SC_NO_CONTENT);
                return "";
        }
    } // END getTraceMap
    
    
    /**
     * Processes a query to the data model for data points
     * 
     * API ENDPOINT: GET /getnextids
     * HTTP GET PARAMETERS: NONE
     * 
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     */
    private Object getNextIds(Request request, Response response){
        // no mzTree assigned or not open yet
        if (mzTree == null || mzTree.getLoadStatus() == ImportState.ImportStatus.NONE) {
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No file has been selected.";
        }

        switch (mzTree.getLoadStatus()) {

            // "loading" status types
            case PARSING:
            case CONVERTING:
            case LOADING_MZTREE:
                response.status(HttpServletResponse.SC_CONFLICT);
                return "The server is selecting or processing a file.";

            // "error" status types
            case ERROR:
                response.status(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return "There was a problem opening the file.";

            case READY:
                // serialize traceMap
                JsonObject nextIdsJSON = new JsonObject();
                Optional<Integer> maxTrace = mzTree.traceMap.keySet().stream().max(Comparator.comparingInt(s -> s));
                Optional<Integer> maxEnvelope = mzTree.traceMap.values().stream().max(Comparator.comparingInt(s -> s));
                
                nextIdsJSON.addProperty("nextTrace", Math.max(0, maxTrace.orElse(0)) + 1);
                nextIdsJSON.addProperty("nextEnvelope", Math.max(0, maxEnvelope.orElse(0)) + 1);
                
                // respond with HTTP 200 OK
                response.status(HttpServletResponse.SC_OK);
                return nextIdsJSON.toString();

            default:

                // default to HTTP 204 NO_CONTENT
                response.status(HttpServletResponse.SC_NO_CONTENT);
                return "";
        }
    } // END getNextIds

    /**
     * Query on the status of the data model
     * 
     * API ENDPOINT: GET /filestatus
     * 
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     */
    private Object fileStatus(Request request, Response response){
        // no mzTree assigned or not open yet
        if (mzTree == null || mzTree.getLoadStatus() == ImportState.ImportStatus.NONE) {
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No file has been selected.";
        }

        switch (mzTree.getLoadStatus()) {

            // "loading" status types
            case PARSING:
            case CONVERTING:
            case LOADING_MZTREE:
                response.status(HttpServletResponse.SC_CONFLICT);
                return mzTree.getLoadStatusString();

            // "error" status types
            case ERROR:
                response.status(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return "There was a problem opening the file.";

            case READY:
                // respond with HTTP 200 OK
                response.status(HttpServletResponse.SC_OK);

                // serialize payload as JSON
                JsonObject payload = new JsonObject();

                // mz min and max
                payload.addProperty("mzmin",mzTree.head.mzMin); payload.addProperty("mzmax",mzTree.head.mzMax);

                // rt min and max
                payload.addProperty("rtmin",mzTree.head.rtMin); payload.addProperty("rtmax",mzTree.head.rtMax);

                // rt min and max
                payload.addProperty("intmin",mzTree.head.intMin); payload.addProperty("intmax",mzTree.head.intMax);

                // point count
                try {
                    payload.addProperty("pointcount", mzTree.dataStorage.getPointCount());
                } catch (Exception ex) {
                    payload.add("pointcount", JsonNull.INSTANCE);
                }

                if (mzTree.intensityTracker != null) {
                    double progress = mzTree.intensityTracker.getProgress();
                    payload.addProperty("progress", progress);
                } else {
                    payload.add("progress", JsonNull.INSTANCE);
                }
                
                return payload;

            default:
                // default to HTTP 204 NO_CONTENT
                response.status(HttpServletResponse.SC_NO_CONTENT);
                return "";
        }
    } // END fileStatus

    /**
     * Updates segmentation data in the mzTree and data storage implementation
     * 
     * API ENDPOINT: POST /updatesegmentation
     * HTTP POST PARAMETERS:
     *      [ <action>, <action>, ... ]
     *      <action> (one of the following):
     *          { "type": "undo" }
     *          { "type": "redo" }
     *          { "type": "set-trace", "trace": <traceid>, "points": [<pointid>, ...] }
     *          { "type": "set-envelope", "envelope": <envelopeid>, "traces": [<traceid>, ...] }
     *          { "type": "rectangle", "bounds": [<lower_mz>, <upper_mz>, <lower_rt>, <upper_rt>], "id": <traceID>, "isAdd": <boolean> }
     * @param request Spark request object containing HTTP request components
     * @param response Spark response object returned to requester
     * @return Server message
     * @return 
     */
    private Object updateSegmentation(Request request, Response response) {
        // respond according to fileStatus
        if (mzTree == null || mzTree.getLoadStatus() != ImportState.ImportStatus.READY) {
            // respond with HTTP 204 NO_CONTENT
            response.status(HttpServletResponse.SC_NO_CONTENT);
            return "No data loaded or not ready.";
        }

        try {
            updateSaveLock.lock();

            // parse root level JSONArray of actions
            Gson gson = new Gson();
            JsonArray actions = gson.fromJson(request.body(), JsonArray.class);

            // iterate through each received action
            for (int i = 0; i < actions.size(); i++) {

                // parse action
                JsonObject action = actions.get(i).getAsJsonObject();

                // get action type
                String actionType = action.get("type").getAsString();

                // command to perform
                Command cmd = null;

                // switch on action type
                switch (actionType) {

                    // undo action
                    case "undo":
                        this.commandStack.undoCommand();
                        if (this.mzTree.intensityTracker != null) {
                            this.mzTree.intensityTracker.initCandidateIndexes();
                        }
                        break;

                    // redo action
                    case "redo":
                        this.commandStack.redoCommand();
                        if (this.mzTree.intensityTracker != null) {
                            this.mzTree.intensityTracker.initCandidateIndexes();
                        }
                        break;

                    // set-trace action (brush tracing)
                    case "set-trace":

                        // parse traceID
                        int traceID = action.get("trace").getAsInt();

                        // parse point IDs
                        Integer[] pointIDs = gson.fromJson(action.get("points").getAsJsonArray(), Integer[].class);

                        // construct trace command
                        cmd = new TraceCommand(mzTree, traceID, pointIDs);
                        break;

                    // set-envelope action
                    case "set-envelope":

                        // parse envelopeID
                        int envelopeID = action.get("envelope").getAsInt();

                        // parse traceIDs
                        Integer[] traceIDs = gson.fromJson(action.get("traces").getAsJsonArray(), Integer[].class);

                        // construct envelope command
                        cmd = new EnvelopeCommand(mzTree, envelopeID, traceIDs);
                        break;
                    // rectangular trace action
                    case "rectangle":

                        // parse traceID
                        int _traceID = action.get("id").getAsInt();

                        // parse rectangle bounds
                        Double[] bounds = gson.fromJson(action.get("bounds").getAsJsonArray(), Double[].class);

                        // parse add flag
                        boolean isAdd = action.get("isAdd").getAsBoolean();

                        // construct rectangular trace command
                        cmd = new RectangularTraceCommand(mzTree, _traceID, bounds, isAdd);
                        break;
                }

                // if there is a new command to perform
                // then perform it via the command stack
                if (cmd != null) {
                    commandStack.doCommand(cmd);
                }
            }

        } 
        catch(Exception e)
        {
            LOGGER.log(Level.WARNING, "Failed to update segmentation", e);
            response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return e.getMessage();
        }
        finally {
            updateSaveLock.unlock();
        }

        // respond with HTTP 200 OK
        response.status(HttpServletResponse.SC_OK);

        return "";

    } // END updateSegmentation
    
    
    private Object getEnvelopeInfo(Request request, Response response) throws Exception {
        String[] idstrs = request.body().split(",");
        Map<Integer, List<IsotopeTrace>> envelopeMap;
        
        try {
            envelopeMap = Arrays.stream(idstrs).map(id -> {
                int envelopeId = Integer.parseInt(id);
                return envelopeId;
            }).collect(Collectors.toMap(id -> id, id -> new ArrayList<IsotopeTrace>()));
        } catch (NumberFormatException ex) {
            response.status(HttpServletResponse.SC_BAD_REQUEST);
            return "Could not interpret ids as integers";
        }
        
        List<IsotopeTrace> traces = mzTree.recompileTraces();
        Map<Integer, IsotopeTrace> tracesById = traces.stream().collect(Collectors.toMap(t -> t.traceID, t -> t));
        

        mzTree.traceMap.forEach((tid, eid) -> {
            if (envelopeMap.containsKey(eid)) {
                envelopeMap.get(eid).add(tracesById.get(tid));
            }
        });
        
        StringBuilder respJson = new StringBuilder("{");
        envelopeMap.forEach((eid, envTraces) -> {
            envTraces.sort(Comparator.comparingDouble(t -> t.centroidMZ));
            double firstTraceMz, firstTraceRt;
            if (envTraces.size() > 0) {
                IsotopeTrace firstTrace = envTraces.get(0);
                firstTraceMz = firstTrace.centroidMZ;
                // TODO: use something like a centroidRT, calculated similarly to centroidMZ
                firstTraceRt = firstTrace.getMaxIntensityRT();
            } else {
                // envelopes with no traces have no useful information
                respJson.append('"').append(eid).append("\":null,");
                return;
            }
            
            double totalIntensity = envTraces.stream().mapToDouble(t -> t.intensitySum).sum();
            
            respJson.append('"').append(eid).append("\":["); // [ begin envelope
            respJson.append(firstTraceMz).append(',').append(firstTraceRt).append(',').append(totalIntensity).append(',');
            
            respJson.append('['); // [ begin trace list
            for (IsotopeTrace trace: envTraces) {
                respJson.append('[').append(trace.centroidMZ).append(',').append(trace.intensitySum).append("],");
            }
            if (envTraces.size() > 0) {
                // trim final "," if there was one
                respJson.delete(respJson.length() - 1, respJson.length());
            }
            respJson.append(']'); // ] end trace list
            
            respJson.append("],"); // ] end envelope
        });

        if (envelopeMap.size() > 0) {
            // trim final "," if there was one
            respJson.delete(respJson.length() - 1, respJson.length());
        }
        respJson.append("}");
        
        return respJson;
    } // END getEnvelopes

    /*****************************************************
    ||                      HELPERS                     ||
    *****************************************************/
    
    /**
     * Serializes a portio of an array of MsDataPoint objects into JSON format
     * @param msData Mass spec dataset
     * @param numPoints Number of points to return
     * @param includeIDs If True traceID and envelopeID are included in serialization
     */
    private static StringBuilder JSONify(List<MsDataPoint> msData, int numPoints, boolean includeIDs)
    {
        // numPoints == 0 implies no limit
        if(numPoints == 0)
            numPoints = Integer.MAX_VALUE;
        
        StringBuilder JSON = new StringBuilder("[");
        if(!includeIDs)
            for(int i = 0; i < numPoints && i < msData.size(); i++)
            {
                MsDataPoint curPoint = msData.get(i);
                JSON.append("[").append(curPoint.mz).append(",")
                        .append(curPoint.rt).append(",")
                        .append(curPoint.intensity).append("],");
            }
                   
        else
            for(int i = 0; i < numPoints && i < msData.size(); i++)
            {
                MsDataPoint curPoint = msData.get(i);
                JSON.append("[")
                        .append(curPoint.pointID).append(",")
                        .append(curPoint.traceID).append(",")
                        .append(curPoint.mz).append(",")
                        .append(curPoint.rt).append(",")
                        .append(curPoint.intensity).append("],");
            }
        
        if(msData.size() > 0)
            JSON.replace(JSON.length()-1, JSON.length(), "]");
        else 
            JSON.append("]");
        
        return JSON;
    }

    /**
     * Stops the Spark web server
     */
    public void stopServer() {
        spark.stop();
    }

    /**
     * Blocks until the spark server is initialized, returning true if it was successful
     */
    public boolean waitUntilStarted() {
        spark.awaitInitialization();
        return this.successfulStart;
    }
    
}