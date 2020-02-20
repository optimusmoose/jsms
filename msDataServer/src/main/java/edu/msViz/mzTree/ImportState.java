/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree;

import java.util.Observable;

/**
 * Observer implementation for tracking the progress of an mzml import
 * or MzTree load
 * @author kyle
 */
public class ImportState extends Observable
{
    // status enumeration
    public enum ImportStatus {
        NONE,                   // no file is being imported
        PARSING,                // parsing or determining file type
        CONVERTING,             // converting to mzTree format and writing mzTree points
        INDEXING,                // creating intensity index
        WRITING,                // writing mzTree nodes
        ERROR,                  // unknown filetype or conversion failure
        LOADING_MZTREE,         // loading mzTree format (fast)
        READY,                  // done importing
    };

    // current import status
    private ImportStatus importStatus;

    // total work to be done (float for percentage calculation)
    private float totalWork;

    // amount of work currently completed
    private int workDone = 0;

    private int percentDone = 0;

    // path to source file (source)
    private String sourceFilePath;

    // path to mzTree file (destination)
    private String mzTreeFilePath;

    public ImportStatus getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(ImportStatus importStatus) {
        this.importStatus = importStatus;
        this.setChanged();
        this.notifyObservers();
    }

    public float getTotalWork() {
        return totalWork;
    }

    public void setTotalWork(float totalWork) {
        this.totalWork = totalWork;
        this.setChanged();
        this.notifyObservers();
    }

    public int getWorkDone() {
        return workDone;
    }

    public void setWorkDone(int workDone) {
        this.workDone = workDone;
        this.setChanged();
        this.notifyObservers();
    }

    public String getSourceFilePath() {
        return sourceFilePath;
    }

    public void setSourceFilePath(String sourceFilePath) {
        this.sourceFilePath = sourceFilePath;
        this.setChanged();
        this.notifyObservers();
    }

    public String getMzTreeFilePath() {
        return mzTreeFilePath;
    }

    public void setMzTreeFilePath(String path){
        this.mzTreeFilePath = path;
        this.setChanged();
        this.notifyObservers();
    }

    public void reset() {
        this.importStatus = ImportStatus.NONE;
        this.totalWork = 0;
        this.workDone = 0;
        this.sourceFilePath = "";
        this.mzTreeFilePath = "";
    }

    /**
     * Reports the progress of an mzml import / mzTree load
     * @return
     */
    public String getStatusString()
    {
        // switch on the status of the import
        switch(this.importStatus){

            case NONE:
              return "No file selected.";

            // parsing mzml
            case PARSING:
              return "Parsing " + this.sourceFilePath;

            // building mzTree from parsed mzml
            case CONVERTING:
                if (this.mzTreeFilePath == null)
                    return "Creating mzTree file";
                else
                {
                    this.percentDone = (int)((this.workDone / this.totalWork) * 100);
                    return "Writing points to " + this.mzTreeFilePath + " || " + percentDone + "%";
                }

            case INDEXING:
              if (this.workDone == 0){
                return "Creating intensity index || sorting points...please be patient";
              } else {
                this.percentDone = (int)((this.workDone / this.totalWork) * 100);
                return "Creating intensity index" + " || " + percentDone + "%";
              }

            case WRITING:
                this.percentDone = (int)((this.workDone / this.totalWork) * 100);
                return "Creating node index" + " || " + percentDone + "%";

            case ERROR:
                return "Failed to open file.";

            // loading MzTree from .mztree
            case LOADING_MZTREE:
                if (this.mzTreeFilePath == null)
                    return "Connecting to MzTree";
                else
                    return "Loading MzTree: " + this.mzTreeFilePath;

            // finished
            case READY:
                return "Import complete: " + this.mzTreeFilePath;

            // default implies error
            default:
                return "Server Error - Import status unknown";
        }
    }
}
