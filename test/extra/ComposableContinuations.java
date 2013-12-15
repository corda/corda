package extra;

import static avian.Continuations.shift;
import static avian.Continuations.reset;

import avian.FunctionReceiver;
import avian.Function;

import java.util.concurrent.Callable;

public class ComposableContinuations {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    expect(2 * reset(new Callable<Integer>() {
          public Integer call() throws Exception {
            return 1 + shift(new FunctionReceiver<Integer, Integer>() {
                public Integer receive
                  (Function<Integer, Integer> continuation)
                  throws Exception
                {
                  return continuation.call(5);
                }
              });
          }
        }) == 12);

    expect(1 + reset(new Callable<Integer>() {
          public Integer call() throws Exception {
            return 2 * shift(new FunctionReceiver<Integer, Integer>() {
                public Integer receive
                  (Function<Integer, Integer> continuation)
                  throws Exception
                {
                  return continuation.call(continuation.call(4));
                }
              });
          }
        }) == 17);
  }
}
