package edu.umt.ms.traceSeg.metrics;

import java.util.Collection;

public class NTCDScoringMethod implements ScoringMethod {
    @Override
    public double calculateScore(PointTraceDatabase truth, PointTraceDatabase prediction, boolean noise) {

        Collection<Integer> includePredictedIds;
        if (noise) {
            includePredictedIds = prediction.getNonNoiseTraceIds();
        } else {
            includePredictedIds = prediction.getTraceIds();
        }

        // SUM (min cluster dist)
        double clusterDistSum = includePredictedIds.stream().mapToDouble(prid -> {
            double prCentroid = prediction.getTraceCentroid(prid);
            // min (for c in C) (w^k - c)
            return truth.getTraceIds().stream().mapToDouble(trid -> Math.abs(truth.getTraceCentroid(trid) - prCentroid)).min().orElse(0.0);
        }).sum();

        // 1 / (min ( |W|, |C| )) * SUM
        return clusterDistSum / (Math.min(truth.getTraceIds().size(), prediction.getTraceIds().size()));
    }
}
