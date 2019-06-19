/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.harnesses;

import edu.msViz.mzTree.MsDataPoint;
import edu.msViz.mzTree.MzTree;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 *
 * @author jeb
 */
public class MzTreeAccess {

    // args <filepath> [<mzmin>] [<mzmax>] [<rtmin>] [<rtmax>] <queryfile> <numpoints>
    // batch: <filepath> <queryfile>
    // summary: <filepath> <queryfile> <numpoints>
    // full: <filepath> <mzmin> <mzmax> <rtmin> <rtmax>
    public static void main(String[] args) {
        try {            
            MzTree tree = new MzTree();
            tree.load(args[0], SummarizationStrategyFactory.Strategy.WeightedStriding);
            
            // default: numPoints = 0 -> no summarization
            int numPoints = 0;

            // batch or summary
            ArrayList<QueryRange> ranges = new ArrayList<>();
            if (args.length == 2 || args.length == 3) {
                try(Scanner fileScanner = new Scanner(new File(args[1]))) {
                    while (fileScanner.hasNextLine()) {
                        try(Scanner lineScanner = new Scanner(fileScanner.nextLine())) {
                            lineScanner.useDelimiter(",");
                            double mzmin = lineScanner.nextDouble();
                            double mzmax = lineScanner.nextDouble();
                            float rtmin = lineScanner.nextFloat();
                            float rtmax = lineScanner.nextFloat();
                            ranges.add(new QueryRange(mzmin, mzmax, rtmin, rtmax));
                        }
                    }
                }
                
                // summary mode with this number of points
                if (args.length == 3) {
                    numPoints = Integer.parseInt(args[2]);
                }
                
            // full
            } else if (args.length == 5) {
                double mzmin = Double.parseDouble(args[1]);
                double mzmax = Double.parseDouble(args[2]);
                float rtmin = Float.parseFloat(args[3]);
                float rtmax = Float.parseFloat(args[4]);
                ranges.add(new QueryRange(mzmin, mzmax, rtmin, rtmax));
            }
            
            for (QueryRange range: ranges) {
                long time = System.currentTimeMillis();
                List<MsDataPoint> data = tree.query(range.mzmin, range.mzmax, range.rtmin, range.rtmax, numPoints);
                assert data.stream().allMatch(x -> x.mz >= range.mzmin && x.mz <= range.mzmax && x.rt >= range.rtmin && x.rt <= range.rtmax);
                System.out.println(data.size() + "," + (System.currentTimeMillis() - time)/1000.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static class QueryRange {
        public double mzmin;
        public double mzmax;
        public float rtmin;
        public float rtmax;
        
        public QueryRange(double mzmin, double mzmax, float rtmin, float rtmax) {
            this.mzmin = mzmin;
            this.mzmax = mzmax;
            this.rtmin = rtmin;
            this.rtmax = rtmax;
        }
    }
}
