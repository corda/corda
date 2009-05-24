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

  public static <T> T dynamicWind(Runnable before,
                                  Callable<T> thunk,
                                  Runnable after)
    throws Exception
  {
    UnwindResult result = dynamicWind2(before, thunk, after);
    if (result.continuation != null) {
      after.run();
      if (result.exception != null) {
        result.continuation.handleException(result.exception);
      } else {
        result.continuation.handleResult(result.result);
      }
      throw new AssertionError();
    } else {
      return (T) result.result;
    }
  }

  private static native UnwindResult dynamicWind2(Runnable before,
                                                  Callable thunk,
                                                  Runnable after)
    throws Exception;

  private static UnwindResult wind(Runnable before,
                                   Callable thunk,
                                   Runnable after)
    throws Exception
  {
    before.run();

    try {
      return new UnwindResult(null, thunk.call(), null);
    } finally {
      after.run();
    }
  }

  private static void rewind(Runnable before,
                             Callback continuation,
                             Object result,
                             Throwable exception)
    throws Exception
  {
    before.run();
    
    if (exception != null) {
      continuation.handleException(exception);
    } else {
      continuation.handleResult(result);
    }

    throw new AssertionError();
  }

  private static class Continuation<T> implements Callback<T> {
    public native void handleResult(T result);
    public native void handleException(Throwable exception);
  }

  private static class UnwindResult {
    public final Callback continuation;
    public final Object result;
    public final Throwable exception;

    public UnwindResult(Callback continuation, Object result,
                        Throwable exception)
    {
      this.continuation = continuation;
      this.result = result;
      this.exception = exception;
    }
  }
}
