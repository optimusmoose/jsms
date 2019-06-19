package edu.msViz.mzTree.IO;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decodes base64 encoded strings into double arrays
 */
public class Decoder {
    /**
     * converts base64 string input to array of doubles
     * @param input base64 encoded data
     * @param isDouble flag to signal if data is 32 or 64 bit floating point
     * @return decoded double array
     */
    public static double[] decodeUncompressed(String input, boolean isDouble) {
        return decodeUncompressed(Base64.getDecoder().decode(input), isDouble);
    }

    /**
     * converts array of bytes to corresponding array of doubles
     * @param binArray array of bytes
     * @param isDouble flag to signal if data is 32 or 64 bit floating point 
     * @return decoded double array
     */
    public static double[] decodeUncompressed(byte[] binArray, boolean isDouble) {
        ByteBuffer buf = ByteBuffer.wrap(binArray).order(ByteOrder.LITTLE_ENDIAN);

        int bytesPer = isDouble ? 8 : 4;
        double[] dataArray = new double[binArray.length / bytesPer];

        for (int i = 0; i < dataArray.length; i++) {
             dataArray[i] = isDouble ? buf.getDouble() : buf.getFloat();
        }
        return dataArray;
    }

    /**
     * converts zlib-compressed base-64 encoded data to array of doubles
     * @param encoding base-64 encoded data
     * @param isDouble flag to signal if data is 32 or 64 bit floating point 
     * @return decoded double array
     * @throws java.util.zip.DataFormatException
     * @throws java.io.IOException
     */ 
    public static double[] decodeCompressed(String encoding, boolean isDouble) throws DataFormatException
    {
        // Decode the Base64 string into a byte array. 
        byte[] binArray = Base64.getDecoder().decode(encoding);

        // decompress zlib	
        Inflater decompressor = new Inflater();
        decompressor.setInput(binArray);

        // Create an expandable byte array to hold the decompressed data
        ByteArrayOutputStream bos = new ByteArrayOutputStream(binArray.length);

        // Decompress the data
        byte[] buf = new byte[1024];
        while (!decompressor.finished()) {
            int count = decompressor.inflate(buf);
            bos.write(buf, 0, count);
        }
        return decodeUncompressed(bos.toByteArray(), isDouble);
    }
}
