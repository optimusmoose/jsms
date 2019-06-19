package edu.umt.ms.traceSeg.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PurityScoringMethod implements ScoringMethod {
    private PointTraceDatabase truth;

    @Override
    public double calculateScore(PointTraceDatabase truth, PointTraceDatabase prediction, boolean noise) {
        this.truth = truth;

        Set<Integer> noisePointIds = new HashSet<>();

        if (noise) {
            noisePointIds.addAll(truth.getTracePointIds(0));
            noisePointIds.addAll(prediction.getTracePointIds(0));
        }

        Collection<Integer> predTraces;
        Collection<Integer> truthTraces;
        if (noise) {
            // ignore the noise trace
            predTraces = prediction.getNonNoiseTraceIds();
            truthTraces = truth.getNonNoiseTraceIds();
        } else {
            // non-noise aware
            predTraces = prediction.getTraceIds();
            truthTraces = truth.getTraceIds();
        }

        // SUM = 1..predictions maxCommonality
        int commonalitySum = predTraces.stream().mapToInt(id -> findLargestCommonality(prediction.getTracePointIds(id), truthTraces, noisePointIds)).sum();
        int dataSetSize = truth.getPoints().size();

        if (noise) {
            dataSetSize -= noisePointIds.size();
        }

        // 1 / |D| * SUM
        return (double)commonalitySum / dataSetSize;
    }

    // max. commonality: max for c in C | w_k or w~_k intersect c |
    private int findLargestCommonality(Collection<Integer> points, Collection<Integer> truthSearchSpaceIds, Set<Integer> noisePointIds) {
        if (noisePointIds == null) {
            noisePointIds = new HashSet<>();
        }

        Set<Integer> finalNoisePointIds = noisePointIds;
        return truthSearchSpaceIds.stream().mapToInt(tid -> {

            Collection<Integer> pointIds = new ArrayList<>(points);
            Collection<Integer> truthPointIds = truth.getTracePointIds(tid);

            pointIds.retainAll(truthPointIds);
            pointIds.removeAll(finalNoisePointIds);
            // | w_k or w~_k intersect c |
            return pointIds.size();
        }).max().orElse(0);
    }
}
