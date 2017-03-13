/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class FileInputStream extends InputStream {
  //   static {
  //     System.loadLibrary("natives");
  //   }

  private int fd;
  private int remaining;

  public FileInputStream(FileDescriptor fd) {
    this.fd = fd.value;
  }

  public FileInputStream(String path) throws IOException {
    fd = open(path);
    remaining = (int) new File(path).length();
  }

  public FileInputStream(File file) throws IOException {
    this(file.getPath());
  }

  public int available() throws IOException {
    return remaining;
  }

  private static native int open(String path) throws IOException;

  private static native int read(int fd) throws IOException;

  private static native int read(int fd, byte[] b, int offset, int length)
    throws IOException;

  public static native void close(int fd) throws IOException;

  public int read() throws IOException {
    int c = read(fd);
    if (c >= 0 && remaining > 0) {
      -- remaining;
    }
    return c;
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }

    if (offset < 0 || offset + length > b.length) {
      throw new ArrayIndexOutOfBoundsException();
    }

    int c = read(fd, b, offset, length);
    if (c > 0 && remaining > 0) {
      remaining -= c;
    }
    return c;
  }

  public void close() throws IOException {
    if (fd != -1) {
      close(fd);
      fd = -1;
    }
  }
}
