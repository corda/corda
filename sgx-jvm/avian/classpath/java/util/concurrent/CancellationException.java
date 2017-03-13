/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public class CancellationException extends IllegalStateException {
  private static final long serialVersionUID = -9202173006928992231L;
  
  public CancellationException() {
    super();
  }
  
  public CancellationException(String message) {
    super(message);
  }
}
