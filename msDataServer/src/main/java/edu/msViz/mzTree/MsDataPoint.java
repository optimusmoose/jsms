/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.msViz.mzTree;

/**
 * Class embodiment of a Mass Spectrometry data point
 * @author Kyle
 */
public class MsDataPoint
{
    // number of bytes required to hold point in main memory
    // calculated using profiler
    // point -> 42 bytes per point object
    // HashMap$Node -> 32 bytes per point
    // Integer (used as hash key) -> 16 bytes per point
    public static byte MEM_NUM_BYTES_PER_POINT = 90;
    
    // number of bytes required to store point on disk (not counting ID)
    // mz: 8 bytes
    // rt: 4 bytes
    // intensity: 8 bytes
    // traceID: 4 bytes
    // total = 8 + 4 + 8 + 4 = 24
    public static byte DISK_NUM_BYTES_PER_POINT = 24;

    // point's global ID, corresponds to position in point file
    public int pointID;
    
    // point's assigned trace
    public int traceID;
    
    // point's mz value
    public double mz;
    
    // point's rt value
    public float rt;
    
    // point's intensity value
    public double intensity;
        
    /**
     * Default constructor accepting point ID and values
     * @param pointID point's assigned ID (originates from mzml parse)
     * @param mz point's mz value
     * @param rt point's rt value
     * @param intensity point's intensity value
     */
    public MsDataPoint(int pointID, double mz, float rt, double intensity)
    {
        this.pointID = pointID;
        this.mz = mz;
        this.rt = rt;
        this.intensity = intensity;
    }
    
    /**
     * Checks if the datapoint is within the bounds of the query
     * @param mzMin query mz lower bound
     * @param mzMax query mz upper bound
     * @param rtMin query rt lower bound
     * @param rtMax query rt upper bound
     * @return true if datapoint is within query bounds, false otherwise
     */
    public boolean isInBounds(double mzMin, double mzMax, float rtMin, float rtMax){
        return (this.mz <= mzMax && this.mz >= mzMin 
                && this.rt <= rtMax && this.rt >= rtMin);
    }
    
    @Override
    public String toString(){
        return String.valueOf(this.traceID) + ","  
                + String.valueOf(this.mz) + "," + String.valueOf(this.rt) + "," + String.valueOf(this.intensity);
    }
    
    @Override
    public boolean equals(Object aThat) {
        if ( this == aThat ) return true;
        if ( !(aThat instanceof MsDataPoint) ) return false;
        
        MsDataPoint that = (MsDataPoint)aThat;
        return this.intensity == that.intensity && this.mz == that.mz && this.rt == that.rt && this.traceID == that.traceID; //&& this.envelopeID == that.envelopeID;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + this.traceID;
        //hash = 41 * hash + this.envelopeID;
        hash = 41 * hash + (int) (Double.doubleToLongBits(this.mz) ^ (Double.doubleToLongBits(this.mz) >>> 32));
        hash = 41 * hash + Float.floatToIntBits(this.rt);
        hash = 41 * hash + (int) (Double.doubleToLongBits(this.intensity) ^ (Double.doubleToLongBits(this.intensity) >>> 32));
        return hash;
    }
}
