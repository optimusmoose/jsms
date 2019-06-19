package edu.umt.ms.traceSeg.metrics;

import edu.umt.ms.traceSeg.Point;

import java.util.Set;

public class SSEScoringMethod implements ScoringMethod {

    private PointTraceDatabase truth;
    private PointTraceDatabase prediction;

    @Override
    public double calculateScore(PointTraceDatabase truth, PointTraceDatabase prediction, boolean noise) {
        this.truth = truth;
        this.prediction = prediction;

        double sum = 0;

        Set<Integer> pointIds = truth.getPoints().keySet();
        // for every point ( d in D )
        for (int id : pointIds) {
            Point pt = truth.getPoint(id);
            sum += calculateSqDistance(pt, pt.traceId, prediction.getPoint(pt.id).traceId, noise);
        }

        return sum;
    }

    private double calculateSqDistance(Point pt, int assignmentTrue, int assignmentPred, boolean noise) {
        if (noise) {
            if (assignmentTrue == 0) {
                if (assignmentPred == 0) {
                    // true noise predicted as noise - no error
                    return 0;
                } else {
                    // true noise predicted as real - (m/z - closestTrueNoise)^2

                    // square: all noise points except the one in question...
                    return Math.pow(truth.getTracePoints(0).stream().filter(p -> p.id != pt.id)

                    // as the difference between their m/z values
                            .mapToDouble(p -> Math.abs(p.mz - pt.mz))

                    // taking the minimum
                            .min().orElse(0), 2);
                }
            } else {
                if (assignmentPred == 0) {
                    // real point predicted as noise - (m/z - c^d)^2
                    return sqDistance(pt.mz, truth.getTraceCentroid(assignmentTrue));
                } else {
                    // real point predicted as real - (w^d - c^d)^2
                    return sqDistance(prediction.getTraceCentroid(assignmentPred), truth.getTraceCentroid(assignmentTrue));
                }
            }

        } else {
            // non-noise-aware calculation - (w^d - c^d)^2
            return sqDistance(prediction.getTraceCentroid(assignmentPred), truth.getTraceCentroid(assignmentTrue));
        }
    }

    private static double sqDistance(double mz1, double mz2) {
        return (mz2 - mz1) * (mz2 - mz1);
    }
}
