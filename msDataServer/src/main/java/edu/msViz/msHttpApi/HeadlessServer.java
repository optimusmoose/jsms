package edu.msViz.msHttpApi;

import java.nio.file.Path;
import java.nio.file.Paths;

import edu.msViz.mzTree.MzTree;
import edu.msViz.mzTree.summarization.SummarizationStrategyFactory.Strategy;

public class HeadlessServer {

	/**
	 * Runs the data server headless in one of several modes.
	 * 
	 * convert: converts an input file to an mzTree file
	 * server: starts the MS HTTP server for a file on a port
	 * 
	 * Example usage:
	 * java -cp /path/to/msDataServer-1.0.jar edu.msViz.msHttpApi.HeadlessServer convert input_file.mzML output_file.mzTree
	 * java -cp /path/to/msDataServer-1.0.jar edu.msViz.msHttpApi.HeadlessServer server file.mzTree 8000 
	 * 
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Expected 'convert' or 'server' operation.");
			return;
		}
		
		if (args[0].equals("convert")) {
			if (args.length < 3) {
				System.out.println("Expected a source and a destination path.");
				return;
			}
			Path sourcePath = Paths.get(args[1]);
			Path destPath = Paths.get(args[2]);
			
			try {
				convert(sourcePath, destPath);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} else if (args[0].equals("server")) {
			if (args.length < 3) {
				System.out.println("Expected a file and a port.");
				return;
			}
			
			Path filePath = Paths.get(args[1]);
			int port;
			try {
				port = Integer.parseInt(args[2]);
			} catch(NumberFormatException ex) {
				System.out.println("Port must be a number.");
				return;
			}
			
			try {
				startServer(filePath, port);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void convert(Path sourceFilePath, Path destFilePath) throws Exception {
		MzTree mzTree = new MzTree();
		mzTree.setConvertDestinationProvider(suggestedPath -> {
			return destFilePath;
		});
		mzTree.load(sourceFilePath.toString(), Strategy.WeightedStriding);
	}
	
	public static void startServer(Path filePath, int port) throws Exception {
		MzTree mzTree = new MzTree();
		mzTree.load(filePath.toString(), Strategy.WeightedStriding);
		
		MsDataServer dataServer = new MsDataServer();
		dataServer.setMzTree(mzTree);
		dataServer.startServer(port);
	}
}
