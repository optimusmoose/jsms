package edu.umt.ms.traceSeg;

/**
 * Defines an API to interface with a segmentation database.
 * The database must provide access to:
 *     * a datawide intensity range
 *     * the highest point that has not yet been segmented
 *     * the next available ID for a trace
 *     * a system for assigning a point to a trace
 */
public interface PointDatabaseConnection {
    double getMinimumIntensity() throws Exception;
    double getMaximumIntensity() throws Exception;
    Point getHighestUnassignedPoint() throws Exception;
    int getNextTraceId() throws Exception;
    void writePoint(int traceId, Point point) throws Exception;
    double getMzResolution(double nearMz) throws Exception;
}
