package edu.msViz.mzTree.storage;

import edu.msViz.mzTree.ImportState;
import edu.msViz.mzTree.ImportState.ImportStatus;
import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.PointCache;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// Tracks traced/untraced points by highest intensity
public class IntensityTracker {

    private static final Logger LOGGER = Logger.getLogger(IntensityTracker.class.getName());

    private RandomAccessFile dataFile;
    private PointCache pointCache;
    private int runs;

    private long[] runStarts;
    private int[] runLengths;

    private int[] candidateIndexes;

    private boolean writing = false;

    public IntensityTracker(String fileName, PointCache pointCache) throws IOException {
        this.pointCache = pointCache;

        dataFile = new RandomAccessFile(fileName, "rw");

        if (dataFile.length() == 0) {
            writing = true;

        } else {
            runs = dataFile.readInt();
            runStarts = new long[runs];

            for (int i = 0; i < runs; i++) {
                runStarts[i] = dataFile.readLong();
            }

            calculateRunLengths();
            initCandidateIndexes();
        }
    }

    // initializes or resets the candidate indexes, forcing the database to be searched again
    public synchronized void initCandidateIndexes() throws IOException {
        candidateIndexes = new int[runs];
        for (int i = 0; i < runs; i++) {
            candidateIndexes[i] = 0;
        }
        findAllUntraced();
    }

    // finds the next untraced point in all runs
    private void findAllUntraced() throws IOException {
        for (int i = 0; i < runs; i++) {
            findUntraced(i);
        }
    }

    // calculate the number of points per run, after run start positions have been read/written
    private void calculateRunLengths() throws IOException {
        runLengths = new int[runs];
        for (int i = 0; i < runs-1; i++) {
            runLengths[i] = (int) ((runStarts[i+1] - runStarts[i])/4);
        }
        runLengths[runs-1] = (int) ((dataFile.length() - runStarts[runs-1])/4);
    }

    // initializes the number of runs that will be written
    public synchronized void setRunCount(int runs) throws IOException {
        if (!writing) {
            throw new UnsupportedOperationException();
        }

        this.runs = runs;
        runStarts = new long[runs];
        runLengths = new int[runs];
        dataFile.seek(0);
        dataFile.writeInt(runs);
        dataFile.seek(4 + 8*runs);
    }

    private int writingRun = 0;
    // adds a run of point IDs
    public synchronized void addRun(int[] pointIds, ImportState importState) throws IOException {
        importState.setTotalWork(pointIds.length);
        importState.setWorkDone(0);
        importState.setImportStatus(ImportStatus.INDEXING);

        if (!writing) {
            throw new UnsupportedOperationException();
        }

        long offset = dataFile.getFilePointer();
        runStarts[writingRun] = offset;

        for (int i = 0; i < pointIds.length; i++) {
            dataFile.writeInt(pointIds[i]);
            if(i % 1000 == 0){
              importState.setWorkDone(i);
            }
        }

        writingRun++;
        dataFile.getFD().sync();
    }

    // finalize internal data structures after all runs have been added
    public synchronized void finishAdding() throws IOException {
        if (!writing) {
            throw new UnsupportedOperationException();
        }

        dataFile.seek(4);
        for (int i = 0; i < runs; i++) {
            dataFile.writeLong(runStarts[i]);
        }

        dataFile.getFD().sync();
        writing = false;

        calculateRunLengths();
        initCandidateIndexes();
    }

    // reads an individual point ID in a run at the given index
    private int get(int run, int index) throws IOException {
        if (run < 0 || run >= runs) {
            throw new IndexOutOfBoundsException();
        }
        if (index < 0 || index >= runLengths[run]) {
            throw new IndexOutOfBoundsException();
        }

        dataFile.seek(runStarts[run] + index * 4);
        return dataFile.readInt();
    }

    // searches through "run" until an untraced point is encountered
    private synchronized void findUntraced(int run) throws IOException {
        if (run < 0 || run >= runs) {
            throw new IndexOutOfBoundsException();
        }

        while (candidateIndexes[run] < runLengths[run]) {
            int pointId = get(run, candidateIndexes[run]);
            MsDataPoint p = pointCache.get(pointId);
            if (p.traceID == 0) {
                return;
            }
            candidateIndexes[run]++;
        }
        candidateIndexes[run] = Integer.MAX_VALUE;
    }

    // retrieve the highest-intensity MsDataPoint with no trace
    public synchronized MsDataPoint getHighestUntraced() {
        // build list of ids from the highest untraced point in each run
        List<Integer> candidateIds = new ArrayList<>(runs);
        try {
            findAllUntraced();
            for (int i = 0; i < runs; i++) {
                if (candidateIndexes[i] != Integer.MAX_VALUE) {
                    candidateIds.add(get(i, candidateIndexes[i]));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve highest untraced value", e);
            return null;
        }

        // return the highest intensity of all points checked
        return candidateIds.stream()
                .map(i -> pointCache.get(i))
                .sorted(Comparator.comparingDouble((MsDataPoint p) -> p.intensity).reversed())
                .findFirst()
                .orElse(null);
    }

    // based on highest-intensity-first workflow, find the current progress
    public synchronized Double getProgress() {
        try {
            findAllUntraced();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to calculate progress", e);
            return null;
        }

        int done = 0;
        int total = 0;
        for (int i = 0; i < runs; i++) {
            // add run's total to total count
            int runTotal = runLengths[i];
            total += runTotal;

            // add the progress through this run to the total progress
            // MAX_VALUE (= past end of run) means whole run is done
            int index = candidateIndexes[i];
            if (index == Integer.MAX_VALUE) {
                done += runTotal;
            } else {
                done += index;
            }
        }
        return (double)done / total;
    }

    public synchronized void close() {
        try {
            dataFile.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not close intensity tracking file", e);
        }
    }
}
