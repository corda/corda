/* Copyright (c) 2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import java.util.concurrent.Callable;

public abstract class Continuations {
  public static native <T> T callWithCurrentContinuation
    (CallbackReceiver<T> receiver) throws Exception;

  public static native <T> T dynamicWind(Runnable before,
                                         Callable<T> thunk,
                                         Runnable after) throws Exception;

  private static class Continuation<T> implements Callback<T> {
    public native void handleResult(T result);
    public native void handleException(Throwable exception);
  }
}
