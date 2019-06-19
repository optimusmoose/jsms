/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.xnet;

import com.opencsv.CSVReader;
import edu.msViz.xnet.dataTypes.TracesBundle;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Collection of utility methods
 * @author kyle
 */
public class Utility 
{
    /**
     * Formats doubles and floats to the correct precision for separation
     * and correlation outcomes
     */
    private static final DecimalFormat FORMATTER = new DecimalFormat("#.000");
    
    /**
     * Truncates doubles to precision set by formatter, returns double
     * @param x double to truncate
     * @return truncated double as double
     */
    public static double dRound(double x)
    {
        return Double.parseDouble(FORMATTER.format(x));
    }
    
    /**
     * Truncates floats to precision set by formatter, returns double
     * @param x float to truncate
     * @return truncated float as double
     */
    public static double dRound(float x)
    {
        return Double.parseDouble(FORMATTER.format(x));
    }
    
    /**
     * Truncates doubles to precision set by formatter, returns float
     * @param x double to truncate
     * @return truncated double as float
     */
    public static float fRound(double x)
    {
        return Float.parseFloat(FORMATTER.format(x));
    }
    
    /**
     * Truncates floats to precision set by formatter, returns float
     * @param x float to truncate
     * @return truncated float as float
     */
    public static float fRound(float x)
    {
        return Float.parseFloat(FORMATTER.format(x));
    }
    
    /**
     * Formats a double to the precision of a separation/correlation outcome
     * @param x double to format
     * @return formatted double
     */
    public static String format(double x)
    {
        String temp = FORMATTER.format(x);
        if(temp.equals("-.000"))
            temp = ".000";
        return temp;
    }
    
    /**
     * Formats a float to the precision of a separation/correlation outcome
     * @param x float to format
     * @return formatted double
     */
    public static String format(float x)
    {
        String temp = FORMATTER.format(x);
        if(temp.equals("-.000"))
            temp = ".000";
        return temp;
    }
    
    /**
     * Returns the index of the value within the list nearest to the sought value
     * @param values values to search through
     * @param compareValue value to compare against
     * @return index of value in values nearest to compareValue
     */
    public static int getNearestIndex(List<Double> values, double compareValue)
    {
        int nearestValueIndex = 0;
        double nearestDifference = Math.abs(compareValue - values.get(0));
        
        for(int i = 1; i < values.size(); i++)
        {
            double difference = Math.abs(compareValue - values.get(i));
            if(difference < nearestDifference)
            {
                nearestDifference = difference;
                nearestValueIndex = i;
            }
        }
        
        return nearestValueIndex;
    }
    
    /**
     * Returns the index of the value within the list nearest to the sought value
     * @param values values to search through
     * @param compareValue value to compare against
     * @return index of value in values nearest to compareValue
     */
    public static int getNearestIndex(double[] values, double compareValue)
    {
        int nearestValueIndex = 0;
        double nearestDifference = Math.abs(compareValue - values[0]);
        
        for(int i = 1; i < values.length; i++)
        {
            double difference = Math.abs(compareValue - values[i]);
            if(difference < nearestDifference)
            {
                nearestDifference = difference;
                nearestValueIndex = i;
            }
        }
        
        return nearestValueIndex;
    }
    
    /**
     * Returns the index of the value within the list nearest to the sought value
     * @param values values to search through
     * @param compareValue value to compare against
     * @return index of value in values nearest to compareValue
     */
    public static int getNearestIndex(Float[] values, double compareValue)
    {
        int nearestValueIndex = 0;
        double nearestDifference = Math.abs(compareValue - values[0]);
        
        for(int i = 1; i < values.length; i++)
        {
            double difference = Math.abs(compareValue - values[i]);
            if(difference < nearestDifference)
            {
                nearestDifference = difference;
                nearestValueIndex = i;
            }
        }
        
        return nearestValueIndex;
    }
    
    /**
     * Converts an array of doubles to an array of strings
     * @param dArr array of doubles to convert
     * @return array of strings
     */
    public static String[] doubleArrToStringArr(double[] dArr)
    {
        String[] sArr = new String[dArr.length];
        for(int i = 0; i < dArr.length; i++)
            sArr[i] = FORMATTER.format(dArr[i]);
        
        return sArr;
    }
    
    /**
     * Converts an array of floats to an array of strings
     * @param fArr array of doubles to convert
     * @return array of strings
     */
    public static String[] floatArrToStringArr(Float[] fArr)
    {
        String[] sArr = new String[fArr.length];
        for(int i = 0; i < fArr.length; i++)
            sArr[i] = FORMATTER.format(fArr[i]);
        
        return sArr;
    }
    
    /**
     * Inclusively converts a range of doubles to a string array, one entry
     * for each element in the range
     * @param start range start
     * @param end range end
     * @param step range step
     * @return array of strings corresponding to elements in range
     */
    public static String[] doubleRangeToStringArr(double start, double end, double step)
    {
        String[] sArr = new String[(int)((end - start) / step + 1)];
        for(int i = 0; i * step + start <= end; i++)
            sArr[i] = FORMATTER.format(i * step + start);
            
        return sArr;
    }
    
    /**
     * Loads the values from a CSV file into a traces collection
     * @param filePath
     * @return bundled trace information
     */
    public static TracesBundle loadValuesFromCSV(String filePath) 
    {  
        File file = new File(filePath);
        return loadValuesFromCSV(file);
    } 
     
    /**
     * Loads the values from a CSV file into a traces collection
     * @param file
     * @return bundled trace information
     */
    public static TracesBundle loadValuesFromCSV(File file) 
    {  
        
        TracesBundle bundle = new TracesBundle();
        try(CSVReader reader = new CSVReader(new FileReader(file)))
        { 
            String[] row = reader.readNext(); 
 
            // allow the first line to fail in case it is a header
            if(row != null)
            {
                try {
                    processCSVRow(row, bundle);
                } catch(NumberFormatException ex) {
                    // probably a header; continue
                }
            } 
 
            // read the remaining lines (now guaranteed no header) 
            while((row = reader.readNext()) != null) 
            { 
                processCSVRow(row, bundle); 
            } 
        } 
        catch(Exception ex) 
        { 
            System.err.println("Could not load MS data from " + file.getAbsolutePath() + " || " + ex.getMessage()); 
            ex.printStackTrace(); 
        } 
         return bundle;
    }
    
    /**
     * Processes an ms data point as CSV row, places into trace bundle
     * @param row ms data point values
     * @param tracesBundle traces bundle to update
     */
    private static void processCSVRow(String[] row, TracesBundle tracesBundle) 
    { 
        //mz, rt, intensity, traceID, envID 
        int traceID = Integer.parseInt(row[3]); 
         
        // only collect points belonging to a trace 
        if(traceID != 0) 
        { 
            Double mz = Double.parseDouble(row[0]); 
            Float rt = Float.parseFloat(row[1]); 
            Double intensity = Double.parseDouble(row[2]); 
            int envID = Integer.parseInt(row[4]); 
             
            tracesBundle.addPoint(traceID, mz, rt, intensity, envID); 
        } 
    }
 
}
