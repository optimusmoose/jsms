package edu.umt.ms.traceSeg.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NMIScoringMethod implements ScoringMethod {
    @Override
    public double calculateScore(PointTraceDatabase truth, PointTraceDatabase prediction, boolean noise) {

        List<List<Integer>> setW, setC;

        if (noise) {
            // Cnmi = C unionAll ({d} for d in C_0)
            setC = new ArrayList<>();
            for (Integer traceId : truth.getTraceIds()) {
                List<Integer> pointIds = truth.getTracePointIds(traceId);
                if (traceId == 0) {
                    // unionAll ({d} for d in C_0)
                    setC.addAll(pointIds.stream().map(pid -> { List<Integer> l = new ArrayList<>(); l.add(pid); return l; }).collect(Collectors.toList()));
                } else {
                    // union C
                    setC.add(pointIds);
                }
            }

            // Wnmi = unionall (w for w in W) \ N, unionAll ({d} for d in N)
            setW = new ArrayList<>();
            for (Integer traceId: prediction.getTraceIds()) {
                List<Integer> pointIds = prediction.getTracePointIds(traceId);

                if (traceId == 0) {
                    // unionAll ({d} for d in N)
                    setW.addAll(pointIds.stream().map(pid -> { List<Integer> l = new ArrayList<>(); l.add(pid); return l; }).collect(Collectors.toList()));
                } else {
                    // union w in W
                    setW.add(pointIds);
                }
            }
        } else {
            // non-noise-aware
            setW = prediction.getTraceIds().stream().map(tid -> prediction.getTracePointIds(tid)).collect(Collectors.toList());
            setC = truth.getTraceIds().stream().map(tid -> truth.getTracePointIds(tid)).collect(Collectors.toList());
        }

        int count = truth.getPoints().size();
        double mutual = calculateMutual(setW, setC, count);
        double avgEntropy = (calculateEntropy(setW, count) + calculateEntropy(setC, count)) / 2;
        return mutual / avgEntropy;
    }

    private double calculateMutual(List<List<Integer>> setW, List<List<Integer>> setC, int datasetSize) {
        return setW.stream().mapToDouble(sw -> setC.stream().mapToDouble(sc -> {
            // commonPoints = sw intersect sc
            List<Integer> commonPoints = new ArrayList<>(sw);
            commonPoints.retainAll(sc);
            int common = commonPoints.size();

            // when common = 0, the logarithm part of the calculation is an undesirable -Infinity
            if (common == 0) {
                return 0;
            } else {
                // (comm / |D|) * log( (|D| * comm) / (|sw| * |sc|)
                double left = (double)common / datasetSize;
                double right = Math.log(datasetSize * (double)common / ((double)sw.size() * sc.size()));

                return left * right;
            }
        }).sum()).sum();
    }

    // H(S) = - sum_{all S} (|s|/|D| * log(|s}/|D|)
    private double calculateEntropy(List<List<Integer>> subsetSizes, int datasetSize) {
        return - subsetSizes.stream().mapToDouble(s -> (double)s.size() / datasetSize * Math.log((double)s.size() / datasetSize)).sum();
    }
}
