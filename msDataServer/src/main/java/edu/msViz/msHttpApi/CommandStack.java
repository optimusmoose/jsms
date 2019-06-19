/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.msHttpApi;

import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.MzTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to maintain an undo and redo log, 
 * respectively containing preceding and succeeding segmentation commands
 * @author andre
 */
public class CommandStack {  
    
    // log of commands that can be undone
    private final ArrayList<Command> undoLog; 
    
    // log of commands that can be redone
    private final ArrayList<Command> redoLog; 
    
    // maximum size of the undo stack
    private final int MAX_UNDO_STACK_SIZE = 10; 
     
    /**
     * Default constructor
     * Inits logs 
     */
    public CommandStack() { 
        this.undoLog = new ArrayList<>(); 
        this.redoLog = new ArrayList<>(); 
    } 
     
    /**
     * Performs a command and adds it to the undo log
     * @param c Command to perform and log
     * @throws java.lang.Exception
     */
    public void doCommand(Command c) throws Exception {
        

        // execute command
        c.doCommand.run();
 
        // add command to undo log
        this.undoLog.add(c);
        
        // trim undo log if over max size
        if (this.undoLog.size() > MAX_UNDO_STACK_SIZE) {
            this.undoLog.remove(0);
        }
        
        // executing a command clears the redo log
        this.redoLog.clear();
    } 
    
    /**
     * Performs the chronologically last command in the undo log
     * @throws java.lang.Exception
     */
    public void undoCommand() throws Exception {
        
        // continue only if there is a command to undo
        if (!this.undoLog.isEmpty()) 
        {
            // pop last element in undoLog
            Command c = this.undoLog.remove(this.undoLog.size() - 1); 
            
            try{
                // perform command's undo function
                c.undoCommand.run();
            }
            catch(Exception e)
            {
                // on exception return command to undo log
                this.undoLog.add(c);
                throw e;
            }
            // add command to redo log
            this.redoLog.add(c);
        }        
    }
    
    /**
     * Performs the chronologically first command in the redo log
     * @throws java.lang.Exception
     */
    public void redoCommand() throws Exception {
        
        // continue only if there is a command to redo
        if (!this.redoLog.isEmpty()) 
        {
            // pop chronologically first command in redoLog
            Command c = this.redoLog.remove(this.redoLog.size() - 1);
                      
            try{
                // perform command's do function
                c.doCommand.run();
            }
            catch(Exception e)
            {
                this.redoLog.add(c);
                throw e;
            }
            
            // add command to undo log
            this.undoLog.add(c);
        }
    }
    
} 

interface ExceptionRunnable {
    public void run() throws Exception;
}

/**
 * Command pattern base class
 * @author andre
 */
class Command { 
    ExceptionRunnable doCommand;
    ExceptionRunnable undoCommand; 
} 

/**
 * Rectangular trace command
 * Implements undo and redo for a rectangular trace segmentation command
 * @author andre
 */
class RectangularTraceCommand extends Command { 
    
    /**
     * Default constructor accepting parameters required to configure the do and
     * undo commands for a rectangular trace
     * @param mzTree MzTree upon which to perform undo/do commands
     * @param traceID trace ID to add or remove from, depending on isAdd
     * @param bounds [mzLower, mzUpper, rtLower, rtUpper] rectangle's bounds
     * @param isAdd If true then the command is a rectangular add, else is a remove
     */
    public RectangularTraceCommand(MzTree mzTree, int traceID, Double[] bounds, boolean isAdd) {
        
        // trace ID to move from
        int fromTraceID;
        
        // trace ID to move to
        int toTraceID;

        // add -> from 0 to new trace ID
        if(isAdd)
        {
            fromTraceID = 0;
            toTraceID = traceID; 
        }
        
        // remove -> from new trace ID to 0
        else
        {
            fromTraceID = traceID;
            toTraceID = 0;
        }

        // query data storage for the points to be updated
        List<MsDataPoint> area = mzTree.query(bounds[0], bounds[1], bounds[2].floatValue(), bounds[3].floatValue(), 0);

        // points within bounds that have traceID == fromID
        ArrayList<Integer> pointIDsList = new ArrayList<>();
        for(MsDataPoint point : area) {
            if (point.traceID == fromTraceID) {
                pointIDsList.add(point.pointID);
                //-1 is same as unmarked (0) when looking for anything unmarked
            }else if (point.traceID == -1 && fromTraceID == 0){
                pointIDsList.add(point.pointID);
            }
        }
        Integer[] pointIDsToUpdate = pointIDsList.toArray(new Integer[0]);

        // if is an add command and the trace does not exist
        if(isAdd && !mzTree.traceMap.containsKey(toTraceID))
        {
            // do: set traceIDs of the points to update to the "to" traceID
            this.doCommand = () -> {
                mzTree.insertTrace(toTraceID, 0);
                mzTree.updateTraces(toTraceID, pointIDsToUpdate);
            };

            // do: set traceIDs of the points to update to the "from" traceID
            this.undoCommand = () -> {
                mzTree.updateTraces(fromTraceID, pointIDsToUpdate);
                mzTree.deleteTrace(toTraceID);
            };

        }
        else
        {
            // do: set traceIDs of the points to update to the "to" traceID
            this.doCommand = () -> { mzTree.updateTraces(toTraceID, pointIDsToUpdate); };

            // do: set traceIDs of the points to update to the "from" traceID
            this.undoCommand = () -> { mzTree.updateTraces(fromTraceID, pointIDsToUpdate); };
        }
    }
} 

/**
 * Brushing trace command
 * Implements undo and redo for a brushed trace segmentation command
 * @author andre
 */
class TraceCommand extends Command {
    
    /**
     * Default constructor accepting parameters required to configure the do and
     * undo commands for a rectangular trace 
     * @param mzTree MzTree upon which to perform undo/do command
     * @param newTraceID trace ID to set for points in pointIDs
     * @param pointIDs IDs of points to have traces updated
     */
    public TraceCommand(MzTree mzTree, int newTraceID, Integer[] pointIDs)
    {
        // resolve the oldTraceID for the undo command
        // due to the yellow rule this can only be 0, or the same traceID, for all points
        int oldTraceID = mzTree.pointCache.get(pointIDs[0]).traceID;
        
        // if the trace already exists
        if(mzTree.traceMap.containsKey(newTraceID))
        {
            // do command: set points specified by pointIDs to have newTraceID
            this.doCommand = () -> { mzTree.updateTraces(newTraceID, pointIDs); };

            // undo command: set points specified by pointIDs to have their previous traceID
            this.undoCommand = () -> { mzTree.updateTraces(oldTraceID, pointIDs); };
        }
        
        // else incorporate trace creation
        else
        {
            // do command: create trace entity (with 0 envelope),
            // set point's specified by pointIDs to have newTraceID
            this.doCommand = () -> {
                // create trace entity
                mzTree.insertTrace(newTraceID, 0);
                mzTree.updateTraces(newTraceID, pointIDs);
            };
            
            
            // undo command: set points specified by pointIDs to have their previous traceID,
            // delete trace entity
            this.undoCommand = () -> {
                mzTree.updateTraces(oldTraceID, pointIDs);
                
                // delete trace entity
                mzTree.deleteTrace(newTraceID);
            };
        }
    }
}

/**
 * Envelope command
 * Implements undo/do functions for creating, adding to, and removing from envelopes
 * @author andre
 */
class EnvelopeCommand extends Command {

    /**
     * Default constructor accepting parameters required to configure the undo 
     * and do functions for an envelope command
     * @param mzTree MzTree upon which to perform undo/do functions
     * @param newEnvelopeID envelope ID to set for each trace specified by traceIDs
     * @param traceIDs IDs of traces to updated
     */
    public EnvelopeCommand(MzTree mzTree, int newEnvelopeID, Integer[] traceIDs)
    {
        // do command: set traces specified by traceIDs to have newEnvelopeID
        this.doCommand = () -> { mzTree.updateEnvelopes(newEnvelopeID, traceIDs); };
        
        // resolve the oldEnvelopeID for the undo command
        // due to the yellow rule this can only be 0, or the same envelopeID, for all points
        int oldEnvelopeID = mzTree.traceMap.getOrDefault(traceIDs[0], 0);
        
        // undo command: set points' specified to pointIDs to have their previous traceID
        this.undoCommand = () -> { mzTree.updateEnvelopes(oldEnvelopeID, traceIDs); };
    }
    
}

 