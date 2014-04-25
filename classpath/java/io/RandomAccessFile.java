/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import java.lang.IllegalArgumentException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class RandomAccessFile {
  private long peer;
  private File file;
  private long position = 0;
  private long length;
  private boolean allowWrite;

  public RandomAccessFile(String name, String mode)
    throws FileNotFoundException
  {
    this(new File(name), mode);
  }

  public RandomAccessFile(File file, String mode)
    throws FileNotFoundException
  {
    if (file == null) throw new NullPointerException();
    if (mode.equals("rw")) allowWrite = true;
    else if (! mode.equals("r")) throw new IllegalArgumentException();
    this.file = file;
    open();
  }

  private void open() throws FileNotFoundException {
    long[] result = new long[2];
    open(file.getPath(), allowWrite, result);
    peer = result[0];
    length = result[1];
  }

  private static native void open(String name, boolean allowWrite, long[] result)
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
    if (position < 0 || (!allowWrite && position > length())) throw new IOException();

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

  public void write(int b) throws IOException {
    int count = writeBytes(peer, position, new byte[] { (byte)b }, 0, 1);
    if (count > 0) position += count;
  }

  private static native int writeBytes(long peer, long position, byte[] buffer,
                                  int offset, int length);

  public void close() throws IOException {
    if (peer != 0) {
      close(peer);
      peer = 0;
    }
  }

  private static native void close(long peer);

  public FileChannel getChannel() {
    return new FileChannel() {
      public void close() {
        if (peer != 0) RandomAccessFile.close(peer);
      }

      public boolean isOpen() {
        return peer != 0;
      }

      public int read(ByteBuffer dst, long position) throws IOException {
        if (!dst.hasArray()) throw new IOException("Cannot handle " + dst.getClass());
	// TODO: this needs to be synchronized on the Buffer, no?
        byte[] array = dst.array();
        return readBytes(peer, position, array, dst.position(), dst.remaining());
      }

      public int read(ByteBuffer dst) throws IOException {
        int count = read(dst, position);
        if (count > 0) position += count;
        return count;
      }

      public int write(ByteBuffer src, long position) throws IOException {
        if (!src.hasArray()) throw new IOException("Cannot handle " + src.getClass());
        byte[] array = src.array();
        return writeBytes(peer, position, array, src.position(), src.remaining());
      }

      public int write(ByteBuffer src) throws IOException {
        int count = write(src, position);
        if (count > 0) position += count;
        return count;
      }

      public long position() throws IOException {
        return getFilePointer();
      }

      public FileChannel position(long position) throws IOException {
        seek(position);
        return this;
      }

      public long size() throws IOException {
        return length();
      }
    };
  }
}
