/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.concurrent;

public class ExecutionException extends Exception {
  private static final long serialVersionUID = 7830266012832686185L;
  
  protected ExecutionException() {
    super();
  }
  
  protected ExecutionException(String message) {
    super(message);
  }
  
  public ExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
  
  public ExecutionException(Throwable cause) {
    super(cause);
  }
}
