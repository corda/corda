/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio;

public abstract class Buffer {
  protected int capacity;
  protected int position;
  protected int limit;

  public int limit() {
    return limit;
  }

  public int remaining() {
    return limit-position;
  }

  public int position() {
    return position;
  }

  public int capacity() {
    return capacity;
  }

  public Buffer limit(int newLimit) {
    limit = newLimit;
    return this;
  }

  public Buffer position(int newPosition) {
    position = newPosition;
    return this;
  }

  public boolean hasRemaining() {
    return remaining() > 0;
  }

  public Buffer clear() {
    position = 0;
    limit = capacity;
    return this;
  }

  public Buffer flip() {
    limit = position;
    position = 0;
    return this;
  }
}
