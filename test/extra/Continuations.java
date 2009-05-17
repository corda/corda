package extra;

import static avian.Continuations.callWithCurrentContinuation;

import avian.CallbackReceiver;
import avian.Callback;

public class Continuations {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    expect(callWithCurrentContinuation(new CallbackReceiver<Integer>() {
          public Integer receive(Callback<Integer> continuation) {
            continuation.handleResult(42);
            throw new RuntimeException("unreachable");
          }
        }) == 42);

    expect(callWithCurrentContinuation(new CallbackReceiver<Integer>() {
          public Integer receive(Callback<Integer> continuation) {
            return 43;
          }
        }) == 43);

    try {
      callWithCurrentContinuation(new CallbackReceiver<Integer>() {
          public Integer receive(Callback<Integer> continuation) {
            continuation.handleException(new MyException());
            throw new RuntimeException("unreachable");
          }
        });
      throw new RuntimeException("unreachable");      
    } catch (MyException e) {
      e.printStackTrace();
    }

    try {
      callWithCurrentContinuation(new CallbackReceiver<Integer>() {
          public Integer receive(Callback<Integer> continuation)
            throws MyException
          {
            throw new MyException();
          }
        });
      throw new RuntimeException("unreachable");
    } catch (MyException e) {
      e.printStackTrace();
    }
  }

  private static class MyException extends Exception { }
}
