package edu.umt.ms.traceSeg.metrics;

/**
 * Scores two PointTraceDatabase instances, one truth and one prediction.
 * Various implementors exist corresponding to different scoring methods.
 */
public interface ScoringMethod {
    /**
     * Calculates and returns the score of the two provided databases.
     * @param truth Ground truth data
     * @param prediction prediction data
     * @param noise true to use a noise-aware calculation; otherwise false. METHOD-DEPENDENT
     * @return The score of the data. METHOD-DEPENDENT! The meaning of the returned score
     *         may vary significantly between scoring methods
     */
    double calculateScore(PointTraceDatabase truth, PointTraceDatabase prediction, boolean noise);
}
