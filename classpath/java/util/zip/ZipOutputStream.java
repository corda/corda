/* Copyright (c) 2008-2015, Avian Contributors

  Permission to use, copy, modify, and/or distribute this software
  for any purpose with or without fee is hereby granted, provided
  that the above copyright notice and this permission notice appear
  in all copies.

  There is NO WARRANTY for this software.  See license.txt for
  details. */

package java.util.zip;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Deflater;


/**
 *  An as simple as possible implementation of ZipOutputStream
 *  Compression method defaults to DEFLATE
 *  All hardcoded defaults match the defaults for openJDK,
 *  including PKZip version, bit flags set, compression level, etc
 *  
 *  @author David Chau
 *  @author Aaron Davis
 *  @author Christopher Jordan
 *  @author Riley Moses
 *
 */
public class ZipOutputStream extends DeflaterOutputStream {
  private static final int SIGNATURE =                    0x04034b50;
  private static final short VERSION =                    0x0014;
  private static final short BITFLAG =                    0x0008;
  private static final short METHOD =                     0x0008;
  private static final int CENTRAL_FILE_HEADER =          0x02014b50;
  private static final int DATA_DESCRIPTER_HEADER =       0x08074b50;
  private static final int END_OF_CENTRAL_DIRECTORY_SIG = 0x06054b50;
  private static final int DEFAULT_LEVEL =                6;

  private static final int INPUT_BUFFER_SIZE =            1024;
  
  private List<ZipEntry> entries;
  private CRC32 crc = new CRC32();
  private ZipEntry currentEntry;        // holder for current entry
  private int bytesWritten;             // a counter for total bytes written
  private int sizeOfCentralDirectory;   // a counter for central dir size

  // these are used for the function write(int b) to provide a speed increase
  private byte[] inputBuffer = new byte[INPUT_BUFFER_SIZE];
  private int bufferIndex;
  

  public ZipOutputStream(OutputStream outStream) {
    super(outStream, new Deflater(DEFAULT_LEVEL, true));
    bytesWritten = 0;
    sizeOfCentralDirectory = 0;
    entries = new ArrayList<ZipEntry>();
  }
  
  public void putNextEntry(ZipEntry e) throws IOException {
    e.offset = bytesWritten;
    currentEntry = e;
    entries.add(e);
    writeLocalHeader(e);
  }
  
  public void closeEntry() throws IOException {
    // write remainder of buffer if partially full
    if (bufferIndex != 0) {
      write(inputBuffer, 0, bufferIndex);
      bufferIndex = 0;
    }

    finish();

    currentEntry.crc = (int) crc.getValue();
    crc.reset();
    writeDataDescriptor(currentEntry);
  }
  
  @Override
  public void write(byte[] b, int offset, int length) throws IOException {
    if (offset < 0 || length < 0 || b.length - (offset + length) < 0)
      throw new IndexOutOfBoundsException();

    currentEntry.uncompSize += length;
    crc.update(b, offset, length);
    currentEntry.crc = (int) crc.getValue();
    
    deflater.setInput(b, offset, length);
    while (deflater.getRemaining() > 0)
      deflate();
  }

  @Override
  public void write(int b) throws IOException {
    inputBuffer[bufferIndex] = (byte)(b & 0xff);
    bufferIndex += 1;
    if (bufferIndex == 1024) {
      write(inputBuffer, 0, bufferIndex);
      bufferIndex = 0;
    }
  }

  private void deflate() throws IOException {
    int len = deflater.deflate(buffer, 0, buffer.length);
    currentEntry.compSize += len;
    bytesWritten += len;
    if (len > 0)
      out.write(buffer, 0, len);
  }

  private void writeLocalHeader(ZipEntry e) throws IOException {
    byte[] tmpBuffer = new byte[30];

    addFourBytes(SIGNATURE, 0, tmpBuffer);        // local header signature
    addTwoBytes(VERSION, 4, tmpBuffer);           // version used
    addTwoBytes(BITFLAG, 6, tmpBuffer);           // flags
    addTwoBytes(METHOD, 8, tmpBuffer);            // compression method
    addFourBytes(e.modTimeDate, 10, tmpBuffer);   // last mod date and time 
    addFourBytes(0, 14, tmpBuffer);               // CRC is 0 for local header

    // with default flag settings (bit 3 set) the compressed and uncompressed size
    // is written here as 0 and written correctly in the data descripter
    addFourBytes(0, 18, tmpBuffer);               // compressed size
    addFourBytes(0, 22, tmpBuffer);               // uncompressed size
    addTwoBytes(e.name.length(), 26, tmpBuffer);  // length of file name

    // extra field length, in this implementation extra field in not used
    addTwoBytes(0, 28, tmpBuffer);

    out.write(tmpBuffer, 0, 30);

    // write file name, return the number of bytes written
    int len = writeUTF8(e.getName());

    bytesWritten += 30 + len;
  }
  
  private void writeDataDescriptor(ZipEntry currentEntry) throws IOException {
    byte[] tmpBuffer = new byte[16];

    addFourBytes(DATA_DESCRIPTER_HEADER, 0, tmpBuffer);  // data descripter header
    addFourBytes(currentEntry.crc, 4, tmpBuffer);        // crc value
    addFourBytes((int)currentEntry.compSize, 8, tmpBuffer);   // compressed size
    addFourBytes((int)currentEntry.uncompSize, 12, tmpBuffer);// uncompressed size
    out.write(tmpBuffer, 0, 16);
    bytesWritten += 16;
  }
  
  private void writeCentralDirectoryHeader(ZipEntry e) throws IOException {
    byte[] tmpBuffer = new byte[46];

    addFourBytes(CENTRAL_FILE_HEADER, 0, tmpBuffer);  // central directory header signature
    addTwoBytes(VERSION, 4, tmpBuffer);               // version made by
    addTwoBytes(VERSION, 6, tmpBuffer);               // version needed
    addTwoBytes(BITFLAG, 8, tmpBuffer);               // flags
    addTwoBytes(METHOD, 10, tmpBuffer);               // compression method
    
    addFourBytes(e.modTimeDate, 12, tmpBuffer);       // last mod date and time
    addFourBytes(e.crc, 16, tmpBuffer);               // crc
    addFourBytes((int)e.compSize, 20, tmpBuffer);          // compressed size
    addFourBytes((int)e.uncompSize, 24, tmpBuffer);        // uncompressed size
   
    addTwoBytes(e.getName().length(), 28, tmpBuffer); // file name length

    // the following 5 fields are all 0 for a simple default compression
    addTwoBytes(0, 30, tmpBuffer);                    // extra field length (not used)
    addTwoBytes(0, 32, tmpBuffer);                    // comment length (not used)
    addTwoBytes(0, 34, tmpBuffer);                    // disk number start
    addTwoBytes(0, 36, tmpBuffer);                    // internal file attribute
    addFourBytes(0, 38, tmpBuffer);                   // external file attribute

    addFourBytes((int) e.offset, 42, tmpBuffer);      // relative offset of local header

    out.write(tmpBuffer, 0, 46);

    int len = writeUTF8(e.getName());

    bytesWritten += 46 + len;
    sizeOfCentralDirectory += 46 + len;
  }
  
  private void writeEndofCentralDirectory(int offset) throws IOException {
    byte[] tmpBuffer = new byte[22];

    short numEntries = (short) entries.size();
    addFourBytes(END_OF_CENTRAL_DIRECTORY_SIG, 0, tmpBuffer);  // end of central directory signature
    addTwoBytes(0, 4, tmpBuffer);                              // disk number
    addTwoBytes(0, 6, tmpBuffer);                              // disk number where central dir starts
    addTwoBytes(numEntries, 8, tmpBuffer);                     // number of entries on this disk
    addTwoBytes(numEntries, 10, tmpBuffer);                    // number of entries in central dir
    addFourBytes(sizeOfCentralDirectory, 12, tmpBuffer);       // length of central directory
    addFourBytes(offset, 16, tmpBuffer);                       // offset of central directory
    addTwoBytes(0, 20, tmpBuffer);                             // length of added comments (not used)
    out.write(tmpBuffer, 0, 22);
    bytesWritten += 22;
  }

  @Override
  public void close() throws IOException {
    int centralDirOffset = bytesWritten;
    for (ZipEntry e : entries)
      writeCentralDirectoryHeader(e);
    writeEndofCentralDirectory(centralDirOffset);
    deflater.dispose();
    out.close();
  }

  @Override
  public void flush() throws IOException {
    out.write(inputBuffer, 0, inputBuffer.length);
    inputBuffer = new byte[INPUT_BUFFER_SIZE];
  }

  private void finish() throws IOException {
    deflater.finish();
    while (!deflater.finished()) {
      deflate();
    }
    deflater.reset();
  }
  
  private void addTwoBytes(int bytes, int offset, byte[] buffer) throws IOException {
    buffer[offset] = (byte) (bytes & 0xff);
    buffer[offset + 1] = (byte) ((bytes >> 8) & 0xff);
  }
  
  private void addFourBytes(int bytes, int offset, byte[] buffer) throws IOException {
    buffer[offset] = (byte) (bytes & 0xff);
    buffer[offset + 1] = (byte) ((bytes >> 8) & 0xff);
    buffer[offset + 2] = (byte) ((bytes >> 16) & 0xff);
    buffer[offset + 3] = (byte) ((bytes >> 24) & 0xff);
  }

  private int writeUTF8(String text) throws IOException {
    byte[] bytes = text.getBytes("UTF-8");
    out.write(bytes, 0, bytes.length);
    return bytes.length;
  }  
}