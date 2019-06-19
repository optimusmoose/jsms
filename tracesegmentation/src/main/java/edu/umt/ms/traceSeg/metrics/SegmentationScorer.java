package edu.umt.ms.traceSeg.metrics;

import java.io.FileNotFoundException;

/**
 * Harness for scoring truth and predicted trace segmentation data
 * Calls into several scoring methods from the Smith-Ventura paper.
 */
public class SegmentationScorer {
    public static void main(String[] args) throws FileNotFoundException {
        String truthFile = "/home/mee/code/msViz/data/tseg/pbsJ_jeb.csv";
        String predFile = "/home/mee/code/msViz/data/tseg/pbsJ_java.mzTree.csv";

        PointTraceDatabase truthDb = new PointTraceDatabase();
        truthDb.load(truthFile);
        PointTraceDatabase predDb = new PointTraceDatabase();
        predDb.load(predFile);

        ScoringMethod sm;
        double score1, score2;

        System.out.format("%s\t%s\t%s\n", "Metric", "NaC", "LNP-I");
        String output_format = "%s\t%.2f\t%.2f\n";

        sm = new PurityScoringMethod();
        score1 = sm.calculateScore(truthDb, predDb, false);
        score2 = sm.calculateScore(truthDb, predDb, true);
        System.out.format(output_format, "Purity", score1, score2);

        sm = new NMIScoringMethod();
        score1 = sm.calculateScore(truthDb, predDb, false);
        score2 = sm.calculateScore(truthDb, predDb, true);
        System.out.format(output_format, "NMI", score1, score2);

        sm = new SSEScoringMethod();
        score1 = sm.calculateScore(truthDb, predDb, false);
        score2 = sm.calculateScore(truthDb, predDb, true);
        System.out.format("%s\t%g\t%g\n", "SSE", score1, score2);

        sm = new NTCDScoringMethod();
        score2 = sm.calculateScore(truthDb, predDb, true);
        System.out.format(output_format, "NTCD", 0f, score2);
    }
}
