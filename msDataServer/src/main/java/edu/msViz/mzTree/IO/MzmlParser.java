/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree.IO;
import edu.msViz.mzTree.MsDataPoint;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;


import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author André
 */
public class MzmlParser {
        
    /**
     * Accession values used by mzML to classify cvParam values
     */
    private static final String ACCESSION_SCAN_START_TIME = "MS:1000016"; // "scan start time"
    private static final String ACCESSION_MS_LEVEL = "MS:1000511"; // "ms level"
    private static final String ACCESSION_MZ_ARRAY = "MS:1000514"; // "m/z array"
    private static final String ACCESSION_INTENSITY_ARRAY = "MS:1000515"; // "intensity array"
    private static final String ACCESSION_32_BIT_FLOAT = "MS:1000521"; // "32-bit float"
    private static final String ACCESSION_64_BIT_FLOAT = "MS:1000523"; // "64-bit float"
    private static final String ACCESSION_ZLIB_COMPRESSION = "MS:1000574"; // "zlib compression"
	
    /**
     * Minimum intensity required for a point to be included in point retrieval
     */
    private static final double MIN_INTENSITY_THRESHOLD = 1;
    
    /**
     * XML stream reader (mzML is XML because... well who knows)
     */
    private XMLStreamReader reader;
    
    /**
     * path to targeted mzml file
     */
    private final String mzmlFilePath;
    
    /**
     * Number of points in the mzml file
     * Null implies no count has been performed yet
     */
    private int numPoints = -1;
    
    /**
     * When reading from mzml a chunk at a time the chunk will almost
     * always terminate amidst a spectrum. Since XmlStreamReader cannot backtrack,
     * we most preserve the paused spectrum in order to pick up where left off
     * on the previous chunk.
     */
    private SpectrumInformation pausedSpecInfo;
    
    /**
     * Index of point in paused spectrum from which to resume next partition
     */
    private int pauseIndex;
    
    /**
     * Number of points to read per partition read
     */
    public int partitionSize;
    
    private int processMsLevel = 1;
    
    /**
     * Sets the ms level that will be processed by the parser
     */
    public void setProcessMsLevel(int processMsLevel) {
        this.processMsLevel = processMsLevel;
    }
    
    /**
     * Default constructor, accepts path to mzML file
     * @param filePath path to mzML file to parse
     * @throws javax.xml.stream.XMLStreamException
     * @throws java.io.FileNotFoundException
     */
    public MzmlParser(String filePath)
    {
        this.mzmlFilePath = filePath;        
    }
    
    /**
     * Counts the points in the targeted mzML file
     * @return the number of points in the mzML file
     * @throws IOException
     * @throws XMLStreamException
     * @throws DataFormatException 
     */
    public int countPoints() throws IOException, XMLStreamException, DataFormatException
    {
        // don't recount!
        if(this.numPoints == -1)
        {
            // instantiate xml reader on mzmlFilePath
            this.reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(this.mzmlFilePath)); 
            
            // count points
            this.numPoints = 0;
            this.getAllPoints(true);
            
            // close reader
            this.reader.close();
        }
      
        return this.numPoints;
    }
    
    /**
     * Initializes a partitioned read of the dataset
     * @param n partition size
     * @throws XMLStreamException
     * @throws FileNotFoundException 
     */
    public void initPartitionedRead(int n) throws XMLStreamException, FileNotFoundException
    {
        // retain the partition size
        this.partitionSize = n;
        
        // instantiate xml reader on mzmlFilePath
        this.reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(this.mzmlFilePath));
        
    }
    
    /**
     * Reads a partition of the dataset. Must be preceded by call to this.beginPartitionedRead
     * @return A partition of the dataset
     * @throws XMLStreamException
     * @throws DataFormatException
     * @throws IOException 
     */
    public List<MsDataPoint> readPartition() throws XMLStreamException, DataFormatException, IOException
    {
        // points collection for this partition
        List<MsDataPoint> results = new ArrayList<>();
        
        // if the paused spectrum info isn't null then process it first
        while(this.pausedSpecInfo != null)
        {
            this.decodeAndCollectData(this.pausedSpecInfo, results, false, this.pauseIndex, this.partitionSize);
        }

        // parsing loop
        while (this.reader.hasNext()) 
        {    
            // proceed cursor
            this.reader.next();

            // look for the start of a "spectrum" element
            if (this.reader.getEventType() == XMLStreamReader.START_ELEMENT && this.reader.getLocalName().equals("spectrum")) 
            {
                
                this.parseSpectrum(results, false, 0, this.partitionSize);
                
                if(results.size() == this.partitionSize)
                    return results;
            }
        }
        
        // if made it this far the partition size does not evenly divide the dataset
        // and this is the final, reduced size partition
        
        // close reader and return current result set
        this.reader.close();
        return results;
    }
    
    
    /**
     * Reads the MS data points contained within the mzML file specified by filepath
     * @return array of MsDataPoints found in file
     * @throws javax.xml.stream.XMLStreamException
     * @throws DataFormatException
     * @throws IOException 
     */
    public List<MsDataPoint> readAllData() throws IOException, XMLStreamException, DataFormatException 
    {
        return this.getAllPoints(false);
    }
    
    /**
     * Gets all the data from the targeted mzml file, optionally counting the data instead of collecting
     * @param isCount if true this method merely accumulates a point count in numPoints member
     * @return Array of MsDataPoints discovered in the mzml file (null if isCount set)
     * @throws IOException
     * @throws XMLStreamException
     * @throws DataFormatException 
     */
    private List<MsDataPoint> getAllPoints(boolean isCount) throws IOException, XMLStreamException, DataFormatException
    {
        // instantiate xml reader on mzmlFilePath
        this.reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(this.mzmlFilePath)); 
        
        // parsing loop
        while (this.reader.hasNext()) 
        {    
            // proceed cursor
            this.reader.next();

            // look for the start of a "run" element
            // Note: only one run element is allowed per mzML file
            if (this.reader.getEventType() == XMLStreamReader.START_ELEMENT && this.reader.getLocalName().equals("run")) 
            {
                // returns all data points within a run
                List<MsDataPoint> data = this.parseRun(isCount);
                
                if(!isCount)
                {
                    this.numPoints = data.size();
                }
        
                this.reader.close();

                return data;
            }
        }
        
        this.reader.close();
        
        // if no run START_ELEMENT is found
        throw new DataFormatException("No run element found in mzML file: " + this.mzmlFilePath);
    }
    
    /**
     * Parses a run element currently pointed to by reader, returning all contained MS datapoints
     * @param isCount flag indicating the data should merely be counted
     * @param count point count accumulator     
     * @return Array of MSDataPoints found within the run
     * @throws XMLStreamException
     * @throws DataFormatException
     * @throws IOException 
     */
    private List<MsDataPoint> parseRun(boolean isCount) throws XMLStreamException, DataFormatException, IOException {
        
        List<MsDataPoint> runPoints = null;
        
        // parsing loop, return at end of run element
        while (this.reader.hasNext()) 
        {
            // proceed parser
            this.reader.next();
            
            // wait for beginning of spectrumList tag
            // Note: only one spectrumList is allowed per run
            if (this.reader.getEventType() == XMLStreamReader.START_ELEMENT && this.reader.getLocalName().equals("spectrumList")) 
            {
                // begin processing spectrumList
                runPoints = this.parseSpectrumList(isCount);
            }
            
            // wait for end of run element to signal run is finished
            if (this.reader.getEventType() == XMLStreamReader.END_ELEMENT && this.reader.getLocalName().equals("run")) 
            {
                return runPoints;
            }  
        }
       
        // if no run END_ELEMENT is found
        throw new DataFormatException();
        
    }
    
    /**
     * Parses the contents of the spectrumList currently pointed to by reader, returns all contained MsDataPoints
     * @param isCount flag indicating the data should merely be counted
     * @param count point count accumulator
     * @return Array of MsDatapoints contained within the spectrum list
     * @throws XMLStreamException
     * @throws DataFormatException
     * @throws IOException 
     */
    private List<MsDataPoint> parseSpectrumList(boolean isCount) throws XMLStreamException, DataFormatException, IOException {
        
        // point results collection
        ArrayList<MsDataPoint> pointResults = new ArrayList<>();
        
        // parsing loop
        while (reader.hasNext()) 
        {
            // proceed cursor
            reader.next();
            
            // parse discovered spectrums, collect contained points
            if (reader.getEventType() == XMLStreamReader.START_ELEMENT && reader.getLocalName().equals("spectrum")) 
            {
                this.parseSpectrum(pointResults, isCount, 0, Integer.MAX_VALUE);
            }
            
            // return points at end of spectrumlist
            if (reader.getEventType() == XMLStreamReader.END_ELEMENT && reader.getLocalName().equals("spectrumList")) 
            {
                return pointResults;
            }
        }
        
        // if no spectrumList END_ELEMENT is found
        throw new DataFormatException();
    }
    
    /**
     * Parses a spectrum element, storing all discovered points in pointCollection
     * @param pointCollection collection to store all discovered points
     * @param isCount flag indicating data should only be counted
     * @param count data count accumulator
     * @throws XMLStreamException
     * @throws DataFormatException
     * @throws IOException 
     */
    private void parseSpectrum(List<MsDataPoint> pointCollection, boolean isCount, int startIndex, int pointLimit) throws XMLStreamException, DataFormatException, IOException {
       
        // a new SpectrumInformation object is created, this stores all the relevant data in cvParams for one spectrum
        SpectrumInformation spectrumInfo = new SpectrumInformation();
        
        // This BinaryDataArray stores values from cvParams examined until it is identified as an intensity array
        // or a m/z array. Then, currentBDA will be assigned to the relevent BinaryDataArray in currentSI.
        // a new currentBDA will be declare when the end of a BinaryDataArray element in the .mzML is reached
        EncodedData currentEncoding = new EncodedData(); 
        
        // assumably, each mzML spectrum element and each mzML binaryDataArray element will have all the necessary values
        // recorded in cvParam elements. There is not yet any behavior in this method to deal with missing data (such as throwing an
        // exception.
        
        // parsing loop
        while (this.reader.hasNext()) 
        {
            // proceed cursor
            this.reader.next();
            
            // START_ELEMENT events to handle
            if (this.reader.getEventType() == XMLStreamReader.START_ELEMENT) 
            {
                // cvParam element
                if (this.reader.getLocalName().equals("cvParam")) 
                {
                    String[] cvParam = examineCVParam(this.reader);
                    this.updateSpectrumInformation(cvParam, spectrumInfo, currentEncoding);
                }
                
                // 64-bit encoded binary data
                if (this.reader.getLocalName().equals("binary")) 
                {
                    currentEncoding.encoding = reader.getElementText();
                    if(currentEncoding.isMz) spectrumInfo.mzEncoding = currentEncoding; 
                    else spectrumInfo.intensityEncoding = currentEncoding;
                }
            }
            
            // END_ELEMENT events to handle
            if (this.reader.getEventType() == XMLStreamReader.END_ELEMENT) 
            {
                // finish 64-bit encoded data array
                if (this.reader.getLocalName().equals("binaryDataArray")) 
                { 
                    // reinstantiate currentEncoding for potential additional encoding 
                    currentEncoding = new EncodedData();
                }
                
                // spectrum finish
                if (this.reader.getLocalName().equals("spectrum")) 
                {
                    if (spectrumInfo.msLevel == this.processMsLevel) {
                        // decode 64-bit encoded data, collect data points 
                        this.decodeAndCollectData(spectrumInfo, pointCollection, isCount, startIndex, pointLimit);
                    }
                    return;
                }
                
            }
        }
    }
    
    /**
     * Decodes the encoded spectrum data, packages as MsDataPoints and inserts points into pointCollection
     * @param currentSpecInfo spectrum bundle to process
     * @param pointCollection container to place discovered MsDataPoints
     * @param isCount flag indicating the data should merely be counted
     * @param count point count accumulator
     * @throws DataFormatException
     * @throws IOException 
     */
    private void decodeAndCollectData(SpectrumInformation currentSpecInfo, List<MsDataPoint> pointCollection, boolean isCount, int startIndex, int pointLimit) throws DataFormatException, IOException 
    {
        // the paused spectrum (if one existed) is no longer paused
        this.pausedSpecInfo = null;
        
        // decode (and if necessary decompress) mz data encoding
        double[] mzArrayDoubles;
        if (currentSpecInfo.mzEncoding.isCompressed) 
            mzArrayDoubles = Decoder.decodeCompressed(currentSpecInfo.mzEncoding.encoding, currentSpecInfo.mzEncoding.bits == 64); 
        else
            mzArrayDoubles = Decoder.decodeUncompressed(currentSpecInfo.mzEncoding.encoding, currentSpecInfo.mzEncoding.bits == 64);

        // decode (and if necessary decompress) intensity data encoding
        double[] intensityArrayDoubles;
        if (currentSpecInfo.intensityEncoding.isCompressed)
            intensityArrayDoubles = Decoder.decodeCompressed(currentSpecInfo.intensityEncoding.encoding, currentSpecInfo.intensityEncoding.bits == 64);
        else
            intensityArrayDoubles = Decoder.decodeUncompressed(currentSpecInfo.intensityEncoding.encoding, currentSpecInfo.intensityEncoding.bits == 64);

        // creates a MsDataPoint for each (mz,rt,int) point and adds to the arrayList
        // terminates if pointCollection reaches pointLimit
        for (int i = startIndex; i < mzArrayDoubles.length; i++) 
        {
            // stop collection if pointCollection reaches limit
            if(pointCollection.size() == pointLimit)
            {
                this.pauseIndex = i;
                this.pausedSpecInfo = currentSpecInfo;
                break;
            }
            
            // if the data point's intensity is below the min threshold then THROW IT OUT
            if (intensityArrayDoubles[i] >= MIN_INTENSITY_THRESHOLD) 
            {
                if(isCount)
                    this.numPoints++;
                else
                {
                    // assign point an ID of 0
                    // its ID will be assigned when written to data store
                    MsDataPoint myTuple = new MsDataPoint(0, mzArrayDoubles[i], (float)currentSpecInfo.scanStartTime, intensityArrayDoubles[i]);
                    pointCollection.add(myTuple);
                }
            }
        }           
    }
    
    /**
     * Updates currentSI with the provided accession entry
     * @param cvParamPair [accession, value] pair from cvParam
     * @param currentSpecInfo spectrum info for current spectrum
     * @param currentEncoding current encoded data object
     * @throws XMLStreamException 
     */
    private void updateSpectrumInformation(String[] cvParamPair, SpectrumInformation currentSpecInfo, EncodedData currentEncoding) {
        
        // null accession value implies badly formatted cvParam
        if (cvParamPair[0] == null) 
        {
            return;
        }
        switch (cvParamPair[0]) 
        {
            case MzmlParser.ACCESSION_SCAN_START_TIME: // "scan start time"
                currentSpecInfo.scanStartTime = Float.parseFloat(cvParamPair[1]);
                break;
        
            case MzmlParser.ACCESSION_MS_LEVEL: // "ms level"
                currentSpecInfo.msLevel = Short.parseShort(cvParamPair[1]);
                break;
            
            case MzmlParser.ACCESSION_MZ_ARRAY: // "m/z array"
                currentEncoding.isMz = true;
                break;
                
            case MzmlParser.ACCESSION_INTENSITY_ARRAY: // "intensity array"
                currentEncoding.isMz = false;
                break;
                
            case MzmlParser.ACCESSION_32_BIT_FLOAT: // "32-bit float"
                currentEncoding.bits = 32;
                break;   
                
            case MzmlParser.ACCESSION_64_BIT_FLOAT: // "64-bit float"
                currentEncoding.bits = 64;
                break;
                
            case MzmlParser.ACCESSION_ZLIB_COMPRESSION: // "zlib compression"
                currentEncoding.isCompressed = true;
                break;
                
            default:
        }
                
    }
    
    /**
     * parses the cvParam pointed to by reader 
     * @param reader xml stream readers
     * @return String array with elements [accession, value]
     * @throws XMLStreamException 
     */
    private String[] examineCVParam(XMLStreamReader reader)
    {
        String accession = reader.getAttributeValue(null, "accession");
        String value = reader.getAttributeValue(null, "value");
        return new String[] {accession, value};
    }  
    
    /**
     * Override Object.finalize to include closing the reader on GC
     * @throws Throwable 
     */
    @Override
    protected void finalize() throws Throwable{
        super.finalize();
        this.reader.close();
    }
       
}

/**
 * Spectrum information bundle
 * @author Andre
 */
class SpectrumInformation 
{
    /**
     * Spectrum's msLevel
     */
    public short msLevel;
    
    /**
     * Spectrum's start time (RT)
     */
    public float scanStartTime;
        
    /**
     * Spectrum's 64-bit encoded mz data
     */
    public EncodedData mzEncoding;
    
    /**
     * Spectrum's 64-bit encoded intensity data
     */
    public EncodedData intensityEncoding;
}

/**
 * 64-bit encoded data bundle
 * @author Andre
 */
class EncodedData {
    
    /**
     * 64 or 32, float precision
     */
    public short bits;
    
    /**
     * flag to signal this array is compressed using zlib compression
     */
    public boolean isCompressed = false;
    
    /**
     * flag to signal that this struct contains mz data
     */
    public boolean isMz;
    
    /**
     * 64-bit encoded data
     */
    public String encoding;
}


