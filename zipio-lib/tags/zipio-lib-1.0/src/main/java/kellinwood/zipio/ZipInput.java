/*
 * Copyright (C) 2010 Ken Ellinwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kellinwood.zipio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;


import kellinwood.logging.LoggerInterface;
import kellinwood.logging.LoggerManager;

/**
 *
 */
public class ZipInput 
{

    static LoggerInterface log;

    public String inputFilename;
    RandomAccessFile in = null;
    long fileLength;
    int scanIterations = 0;

    Map<String,ZioEntry> zioEntries = new LinkedHashMap<String,ZioEntry>();
    CentralEnd centralEnd;
    Manifest manifest;

    public ZipInput( String filename) throws IOException
    {
        this.inputFilename = filename;
        in = new RandomAccessFile( new File( inputFilename), "r");
        fileLength = in.length();
    }

    private static LoggerInterface getLogger() {
        if (log == null) log = LoggerManager.getLogger(ZipInput.class.getName());
        return log;
    }

    public static ZipInput read( String filename) throws IOException {
        ZipInput zipInput = new ZipInput( filename);
        zipInput.doRead();
        return zipInput;
    }
    
    
    public ZioEntry getEntry( String filename) {
        return zioEntries.get(filename);
    }
    
    public Map<String,ZioEntry> getEntries() {
        return zioEntries;
    }
    
    public Manifest getManifest() throws IOException {
        if (manifest == null) {
            ZioEntry e = zioEntries.get("META-INF/MANIFEST.MF");
            if (e != null) {
                manifest = new Manifest( e.getInputStream());
            }
        }
        return manifest; 
    }

    /** Scan the end of the file for the end of central directory record (EOCDR).
        Returns the file offset of the EOCD signature.  The size parameter is an
        initial buffer size (e.g., 256).
     */
    public long scanForEOCDR( int size) throws IOException {
        if (size > fileLength || size > 65536) throw new IllegalStateException( "End of central directory not found in " + inputFilename);

        int scanSize = (int)Math.min( fileLength, size);

        byte[] scanBuf = new byte[scanSize];

        in.seek( fileLength - scanSize);

        in.readFully( scanBuf);

        for (int i = scanSize - 22; i >= 0; i--) {
            scanIterations += 1;
            if (scanBuf[i] == 0x50 && scanBuf[i+1] == 0x4b && scanBuf[i+2] == 0x05 && scanBuf[i+3] == 0x06) {
                return fileLength - scanSize + i;
            }
        }

        return scanForEOCDR( size * 2);
    }
                              
        
    private void doRead()
    {
        try {

            long posEOCDR = scanForEOCDR( 256);
            in.seek( posEOCDR);
            centralEnd = CentralEnd.read( this);
            DateFormat dateFormat = null;

            boolean debug = getLogger().isDebugEnabled();
            if (debug) {
                getLogger().debug(String.format("EOCD found in %d iterations", scanIterations));
                getLogger().debug(String.format("Directory entries=%d, size=%d, offset=%d/0x%08x", centralEnd.totalCentralEntries,
                                                centralEnd.centralDirectorySize, centralEnd.centralStartOffset, centralEnd.centralStartOffset));

                getLogger().debug(" Length   Method    Size  Ratio   Date   Time   CRC-32    Name");
                getLogger().debug("--------  ------  ------- -----   ----   ----   ------    ----");
                dateFormat = new SimpleDateFormat("MM-dd-yy HH:mm");
            }

            in.seek( centralEnd.centralStartOffset);            

            for (int i = 0; i < centralEnd.totalCentralEntries; i++) {
                ZioEntry entry = ZioEntry.read(this);
                zioEntries.put( entry.getName(), entry);
                if (debug) {
                    int ratio = 0;
                    if (entry.getSize() > 0) ratio = (100 * (entry.getSize() - entry.getCompressedSize())) / entry.getSize();
                    getLogger().debug(String.format("%8d  %6s %8d %4d%% %s  %08x  %s",
                                             entry.getSize(),
                                             entry.getCompression() == 0 ? "Stored" : "Defl:N",
                                             entry.getCompressedSize(),
                                             ratio,
                                             dateFormat.format( new Date( entry.getTime())),
                                             entry.getCrc32(),
                                             entry.getName()));
                }
            }

            /*
            for (ZioEntry entry : zioEntries.values()) {
                entry.readLocalHeader();
            }
            */
            
        }
        catch (Throwable t) {
            t.printStackTrace();
        }    	
    }

    public void close() {
        if (in != null) try { in.close(); } catch( Throwable t) {}
    }

    public long getFilePointer() throws IOException {
        return in.getFilePointer(); 
    }

    public void seek( long position) throws IOException {
        in.seek(position);
    }

    public byte readByte() throws IOException {
        return in.readByte();
    }
    
    public int readInt() throws IOException{
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (in.readUnsignedByte() << (8 * i));
        }
        return result;
    }

    public short readShort() throws IOException {
        short result = 0;
        for (int i = 0; i < 2; i++) {
            result |= (in.readUnsignedByte() << (8 * i));
        }
        return result;
    }

    public String readString( int length) throws IOException {

        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readByte();
        }
        return new String(buffer);
    }

    public byte[] readBytes( int length) throws IOException {

        byte[] buffer = new byte[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = in.readByte();
        }
        return buffer;
    }


}


