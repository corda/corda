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
            return 1 + shift(new FunctionReceiver<Integer>() {
                public Integer receive(Function<Integer> continuation)
                  throws Exception
                {
                  return continuation.call(5);
                }
              });
          }
        }) == 12);

    expect(1 + reset(new Callable<Integer>() {
          public Integer call() throws Exception {
            return 2 * shift(new FunctionReceiver<Integer>() {
                public Integer receive(Function<Integer> continuation)
                  throws Exception
                {
                  return continuation.call(continuation.call(4));
                }
              });
          }
        }) == 17);

    expect(equal(reset(new Callable<Cell<Integer>>() {
            public Cell<Integer> call() throws Exception {
              shift(new FunctionReceiver<Cell<Integer>>() {
                  public Cell<Integer> receive
                    (Function<Cell<Integer>> continuation)
                    throws Exception
                  {
                    return cons(1, continuation.call(null));
                  }
                });

              shift(new FunctionReceiver<Cell<Integer>>() {
                  public Cell<Integer> receive
                    (Function<Cell<Integer>> continuation)
                    throws Exception
                  {
                    return cons(2, continuation.call(null));
                  }
                });

              return null;
            }
          }), cons(1, cons(2, null))));
  }

  private static <T> boolean equal(T a, T b) {
    return (a == null && b == null) || (a != null && a.equals(b));
  }
  
  private static <Car> boolean equal(Cell<Car> a, Cell<Car> b) {
    while (a != null) {
      if (b == null || (! equal(a.car, b.car))) {
        return false;
      }
      a = a.cdr;
      b = b.cdr;
    }

    return b == null;
  }

  private static <Car> Cell<Car> cons(Car car, Cell<Car> cdr) {
    return new Cell(car, cdr);
  }

  private static class Cell<Car> {
    public final Car car;
    public final Cell<Car> cdr;

    public Cell(Car car, Cell<Car> cdr) {
      this.car = car;
      this.cdr = cdr;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("(");
      for (Cell<Car> c = this; c != null; c = c.cdr) {
        sb.append(c.car);
        if (c.cdr != null) {
          sb.append(", ");
        }
      }
      sb.append(")");
      return sb.toString();
    }
  }
}
