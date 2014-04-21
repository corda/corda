/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class FileChannel implements Channel {

  public static enum MapMode {
    PRIVATE, READ_ONLY, READ_WRITE
  };

  public abstract int read(ByteBuffer dst) throws IOException;

  public abstract int read(ByteBuffer dst, long position) throws IOException;

  public abstract int write(ByteBuffer dst) throws IOException;

  public abstract int write(ByteBuffer dst, long position) throws IOException;

  public abstract long position() throws IOException;

  public abstract FileChannel position(long position) throws IOException;

  public abstract long size() throws IOException;
}
