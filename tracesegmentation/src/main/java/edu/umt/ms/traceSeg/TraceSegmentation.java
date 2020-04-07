package edu.umt.ms.traceSeg;

import java.net.URI;
import java.net.URISyntaxException;

class TraceSegmentation {

    /**
     * Runs trace segmentation against an already-running MsDataServer accessible at http://localhost:4567
     */
    public static void main(String[] args) {
        TraceSegmenter segmenter;

        try {
            segmenter = new TraceSegmenter(new HttpPointDatabaseConnection(new URI("http://localhost:4567/")));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        try {
            segmenter.run();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Finished.");
    }
}
