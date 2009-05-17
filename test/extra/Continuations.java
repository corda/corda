package extra;

import static avian.Continuations.callWithCurrentContinuation;

import avian.CallbackReceiver;
import avian.Callback;

public class Continuations {
  public static void main(String[] args) throws Exception {
    System.out.println
      ("result: " +
       callWithCurrentContinuation(new CallbackReceiver<Integer>() {
           public Integer receive(Callback<Integer> continuation) {
             continuation.handleResult(42);
             throw new RuntimeException("unreachable");
           }
         }));

    System.out.println
      ("result: " +
       callWithCurrentContinuation(new CallbackReceiver<Integer>() {
           public Integer receive(Callback<Integer> continuation) {
             return 43;
           }
         }));

    try {
      callWithCurrentContinuation(new CallbackReceiver<Integer>() {
          public Integer receive(Callback<Integer> continuation) {
            continuation.handleException(new MyException());
            throw new RuntimeException("unreachable");
          }
        });
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
    } catch (MyException e) {
      e.printStackTrace();
    }
  }

  private static class MyException extends Exception { }
}
