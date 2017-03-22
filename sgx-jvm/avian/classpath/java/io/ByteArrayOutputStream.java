/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class ByteArrayOutputStream extends OutputStream {
  private static final int BufferSize = 32;

  private Cell firstCell;
  private Cell curCell;
  private int length;
  private byte[] buffer;
  private int position;

  public ByteArrayOutputStream(int capacity) { }

  public ByteArrayOutputStream() {
    this(0);
  }

  public void reset() {
    firstCell = null;
    curCell = null;
    length = 0;
    buffer = null;
    position = 0;
  }

  public int size() {
    return length;
  }

  public void write(int c) {
    if (buffer == null) {
      buffer = new byte[BufferSize];
    } else if (position >= buffer.length) {
      flushBuffer();
      buffer = new byte[BufferSize];
    }

    buffer[position++] = (byte) (c & 0xFF);
    ++ length;
  }

  private byte[] copy(byte[] b, int offset, int length) {
    byte[] array = new byte[length];
    System.arraycopy(b, offset, array, 0, length);
    return array;
  }

  public void write(byte[] b, int offset, int length) {
    if (b == null) {
      throw new NullPointerException();
    }

    if (offset < 0 || offset + length > b.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    if (length == 0) return;

    if (buffer != null && length <= buffer.length - position) {
      System.arraycopy(b, offset, buffer, position, length);
      position += length;
    } else {
      flushBuffer();
      chainCell( new Cell(copy(b, offset, length), 0, length) );
    }

    this.length += length;
  }
  
  private void chainCell(Cell cell){
    if (curCell == null){
      firstCell = cell;
      curCell = cell;
    }else{
      curCell.next = cell;
      curCell = cell;
    }
  }

  private void flushBuffer() {
    if (position > 0) {
      byte[] b = buffer;
      int p = position;
      buffer = null;
      position = 0;

      chainCell( new Cell(b, 0, p) );
    }    
  }

  public byte[] toByteArray() {
    flushBuffer();
    
    byte[] array = new byte[length];
    int pos = 0;
    for (Cell c = firstCell; c != null; c = c.next) {
      System.arraycopy(c.array, c.offset, array, pos, c.length);
      pos += c.length;
    }
    return array;
  }
  
  public synchronized void writeTo(OutputStream out) throws IOException {
    if (length==0) return;

    if (out == null){
      throw new NullPointerException();
    }

    flushBuffer();

    for (Cell c = firstCell; c != null; c = c.next) {
      out.write(c.array, c.offset, c.length);
    }
  }
  
  @Override
  public String toString() {
    return new String(toByteArray());
  }

  public String toString(String encoding) throws UnsupportedEncodingException {
    return new String(toByteArray(), encoding);
  }

  private static class Cell {
    public byte[] array;
    public int offset;
    public int length;
    public Cell next = null;

    public Cell(byte[] array, int offset, int length) {
      this.array = array;
      this.offset = offset;
      this.length = length;
    }
  }
}
