package edu.msViz.mzTree.harnesses;

import edu.msViz.msHttpApi.MsDataServer;
import edu.msViz.msHttpApi.MzTreePointDatabaseConnection;
import edu.msViz.mzTree.MzTree;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory;
import edu.umt.ms.traceSeg.ConsoleTraceParametersProvider;
import edu.umt.ms.traceSeg.TraceSegmenter;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Segmentation {
    private static final Logger LOGGER = Logger.getLogger(Segmentation.class.getName());

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Required exactly one argument.");
            System.exit(1);
        }

        // load mzTree from file
        MzTree mzTree = new MzTree();
        mzTree.load(args[0], SummarizationStrategyFactory.Strategy.WeightedStriding);

        // run server
        MsDataServer server = new MsDataServer();
        server.setMzTree(mzTree);
        server.startServer(4567);

        // run trace segmenter
        MzTreePointDatabaseConnection connection = new MzTreePointDatabaseConnection(mzTree);
        LOGGER.log(Level.INFO, "Beginning trace segmentation");
        long start = System.currentTimeMillis();
        TraceSegmenter segmenter = new TraceSegmenter(connection, new ConsoleTraceParametersProvider());
        segmenter.run();
        LOGGER.log(Level.INFO, "Trace segmentation finished in " + (System.currentTimeMillis() - start) + "ms");
        mzTree.pointCache.clear();

        // run envelope segmentation
        LOGGER.log(Level.INFO, "Beginning envelope segmentation");
        start = System.currentTimeMillis();
        mzTree.executeClusteringCommand("Bayesian", false, null);
        LOGGER.log(Level.INFO, "Envelope segmentation finished in " + (System.currentTimeMillis() - start) + "ms");

        mzTree.close();
        server.stopServer();
    }
}
