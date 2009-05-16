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
             throw new RuntimeException();
           }
         }));
  }
}
