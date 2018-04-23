package extra;

import static avian.Continuations.shift;
import static avian.Cell.cons;
import static avian.Cell.equal;

import avian.Cell;
import avian.Function;
import avian.Continuations;

import java.util.concurrent.Callable;

public class ComposableContinuations {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    expect(2 * Continuations.<Integer,Integer>reset(new Callable<Integer>() {
          public Integer call() throws Exception {
            return 1 + shift
              (new Function<Function<Integer,Integer>,Integer>() {
                public Integer call(Function<Integer,Integer> continuation)
                  throws Exception
                {
                  return continuation.call(5);
                }
              });
          }
        }) == 12);

    expect(1 + Continuations.<Integer,Integer>reset(new Callable<Integer>() {
          public Integer call() throws Exception {
            return 2 * shift
              (new Function<Function<Integer,Integer>,Integer>() {
                public Integer call(Function<Integer,Integer> continuation)
                  throws Exception
                {
                  return continuation.call(continuation.call(4));
                }
              });
          }
        }) == 17);

    expect
      (equal
       (Continuations.<Cell<Integer>,Cell<Integer>>reset
        (new Callable<Cell<Integer>>() {
          public Cell<Integer> call() throws Exception {
            shift(new Function<Function<Cell<Integer>,Cell<Integer>>,
                  Cell<Integer>>()
                  {
                    public Cell<Integer> call
                      (Function<Cell<Integer>,Cell<Integer>> continuation)
                      throws Exception
                    {
                      return cons(1, continuation.call(null));
                    }
                  });

            shift(new Function<Function<Cell<Integer>,Cell<Integer>>,
                  Cell<Integer>>()
                  {
                    public Cell<Integer> call
                      (Function<Cell<Integer>,Cell<Integer>> continuation)
                      throws Exception
                    {
                      return cons(2, continuation.call(null));
                    }
                  });

            return null;
          }
        }), cons(1, cons(2, null))));

    expect
      (equal
       (Continuations.<String,Integer>reset
        (new Callable<String>() {
          public String call() throws Exception {
            return new String
              (shift(new Function<Function<byte[],String>,Integer>() {
                  public Integer call(Function<byte[],String> continuation)
                    throws Exception
                  {
                    return Integer.parseInt
                      (continuation.call(new byte[] { 0x34, 0x32 }));
                  }
                }), "UTF-8");
          }
        }), 42));
  }
}
