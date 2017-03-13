/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public class RejectedExecutionException extends RuntimeException {
  private static final long serialVersionUID = -375805702767069545L;
  
  public RejectedExecutionException() {
    super();
  }
  
  public RejectedExecutionException(String message) {
    super(message);
  }
  
  public RejectedExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
  
  public RejectedExecutionException(Throwable cause) {
    super(cause);
  }
}
