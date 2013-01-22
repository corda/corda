/* Copyright (c) 2008-2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import java.lang.IllegalArgumentException;

public class RandomAccessFile {
  private long peer;
  private File file;
  private long position = 0;
  private long length;

  public RandomAccessFile(String name, String mode)
    throws FileNotFoundException
  {
    if (! mode.equals("r")) throw new IllegalArgumentException();
    file = new File(name);
    open();
  }

  private void open() throws FileNotFoundException {
    long[] result = new long[2];
    open(file.getPath(), result);
    peer = result[0];
    length = result[1];
  }

  private static native void open(String name, long[] result)
    throws FileNotFoundException;

  private void refresh() throws IOException {
    if (file.length() != length) {
      close();
      open();
    }
  }

  public long length() throws IOException {
    refresh();
    return length;
  }

  public long getFilePointer() throws IOException {
    return position;
  }

  public void seek(long position) throws IOException {
    if (position < 0 || position > length()) throw new IOException();

    this.position = position;
  }

  public int skipBytes(int count) throws IOException {
    if (position + count > length()) throw new IOException();
    this.position = position + count;
    return count;
  }
  
  public int read(byte b[], int off, int len) throws IOException {
    if(b == null)
	  throw new IllegalArgumentException();
    if (peer == 0)
	  throw new IOException();
	if(len == 0)
	  return 0;
	if (position + len > this.length)
      throw new EOFException();
	if (off < 0 || off + len > b.length)
      throw new ArrayIndexOutOfBoundsException();
    int bytesRead = readBytes(peer, position, b, off, len);
	position += bytesRead;
	return bytesRead;
  }
  
  public int read(byte b[]) throws IOException {
    if(b == null)
	  throw new IllegalArgumentException();
    if (peer == 0)
	  throw new IOException();
	if(b.length == 0)
	  return 0;
	if (position + b.length > this.length)
      throw new EOFException();
    int bytesRead = readBytes(peer, position, b, 0, b.length);
	position += bytesRead;
	return bytesRead;
  }

  public void readFully(byte b[], int off, int len) throws IOException {
    if(b == null)
	  throw new IllegalArgumentException();
    if (peer == 0)
	  throw new IOException();
	if(len == 0)
	  return;
	if (position + len > this.length)
      throw new EOFException();
	if (off < 0 || off + len > b.length)
      throw new ArrayIndexOutOfBoundsException();
    int n = 0;
    do {
      int count = readBytes(peer, position, b, off + n, len - n);
      position += count;
      if (count == 0)
        throw new EOFException();
      n += count;
    } while (n < len);
  }
  
  public void readFully(byte b[]) throws IOException {
    readFully(b, 0, b.length);
  }

  private static native int readBytes(long peer, long position, byte[] buffer,
                                  int offset, int length);

  public void close() throws IOException {
    if (peer != 0) {
      close(peer);
      peer = 0;
    }
  }

  private static native void close(long peer);
}
