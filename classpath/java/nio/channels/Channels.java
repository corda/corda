/* Copyright (c) 2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class Channels {
  public static InputStream newInputStream(ReadableByteChannel channel) {
    return new MyInputStream(channel);
  }

  public static OutputStream newOutputStream(WritableByteChannel channel) {
    return new MyOutputStream(channel);
  }

  private static class MyInputStream extends InputStream {
    private final ReadableByteChannel channel;

    public MyInputStream(ReadableByteChannel channel) {
      this.channel = channel;
    }

    public int read() throws IOException {
      byte[] buffer = new byte[1];
      int r = read(buffer);
      if (r == -1) {
        return -1;
      } else {
        return buffer[0] & 0xFF;
      }
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
      return channel.read(ByteBuffer.wrap(buffer, offset, length));
    }

    public void close() throws IOException {
      channel.close();
    }
  }

  private static class MyOutputStream extends OutputStream {
    private final WritableByteChannel channel;

    public MyOutputStream(WritableByteChannel channel) {
      this.channel = channel;
    }

    public void write(int v) throws IOException {
      byte[] buffer = new byte[] { (byte) (v & 0xFF) };
      write(buffer);
    }

    public void write(byte[] buffer, int offset, int length)
      throws IOException
    {
      channel.write(ByteBuffer.wrap(buffer, offset, length));
    }

    public void close() throws IOException {
      channel.close();
    }
  }
}
