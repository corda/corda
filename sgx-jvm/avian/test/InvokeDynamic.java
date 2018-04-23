import java.util.*;

public class InvokeDynamic {
  private final int foo;

  private InvokeDynamic(int foo) {
    this.foo = foo;
  }

  private interface Operation {
    int operate(int a, int b);
  }

  private interface Operation2 {
    long operate(long a, int b);
  }

  private static class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
      this.first = first;
      this.second = second;
    }
  }

  private interface Supplier<T> extends java.io.Serializable {
    T get();
  }

  private interface Consumer<T> {
    void accept(T obj);
  }

  private interface Function<T, R> {
    R apply(T obj);
  }

  private interface BiFunction<T, U, R> {
    R apply(T t, U u);
  }

  private interface GetLong {
    long get(long l);
  }

  private interface GetDouble {
    double get(double d);
  }

  private static class LongHolder implements GetLong {
    @Override
    public long get(long l) {
        return l;
    }
  }

  private static class DoubleHolder implements GetDouble {
    @Override
    public double get(double d) {
        return d;
    }
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    int c = 4;
    Operation op = (a, b) -> a + b - c;
    expect(op.operate(2, 3) == (2 + 3) - 4);

    for (int i = 0; i < 4; ++i) {
      new InvokeDynamic(i).test();
    }
  }

  private interface Foo extends java.io.Serializable {
    void someFunction(Integer a, Integer b, String s);
  }

  private interface UnboxedSerializable extends java.io.Serializable {
    int add(int a, int b);
  }

  private interface Unboxed {
    int add(int a, int b);
  }

  private void requiresBridge(Number a, Object... rest) {
    String s = "" + a;
    for (Object r : rest) {
      s += r;
    }
  }

  private static Integer addBoxed(Integer a, Integer b) {
    return a + b;
  }

  private interface Marker {
  }

  private void test() {
    { int c = 2;
      Operation op = (a, b) -> ((a + b) * c) - foo;
      expect(op.operate(2, 3) == ((2 + 3) * 2) - foo);
    }

    { int c = 2;
      Operation2 op = (a, b) -> ((a + b) * c) - foo;
      expect(op.operate(2, 3) == ((2 + 3) * 2) - foo);
    }

    { Supplier<Pair<Long, Double>> s = () -> new Pair<Long, Double>(42L, 77.1D);
      expect(s.get().first == 42L);
      expect(s.get().second == 77.1D);
    }

    { double[] a = new double[] { 3.14D };
      Supplier<Pair<Long, Double>> s = () -> new Pair<Long, Double>(42L, a[0]);
      expect(s.get().first == 42L);
      expect(s.get().second == 3.14D);
    }

    { Foo s = this::requiresBridge;
      s.someFunction(1, 2, "");
    }

    { Consumer<String> c = System.out::println;
      c.accept("invoke virtual");
    }

    { Function<CharSequence, String> f = CharSequence::toString;
      expect(f.apply("invoke interface") == "invoke interface");
    }

    { Function<CharSequence, Integer> f = CharSequence::length;
      expect(f.apply("invoke interface") == 16);
    }

    { BiFunction<CharSequence, Integer, Character> f = CharSequence::charAt;
      String data = "0123456789";
      for (int i = 0; i < data.length(); ++i) {
        expect(f.apply(data, i) == data.charAt(i));
      }
    }

    { Function<java.util.List<String>, Iterator<String>> f = java.util.List<String>::iterator;
      Iterator<String> iter = f.apply(Arrays.asList("1", "22", "333"));
      expect(iter.next() == "1");
      expect(iter.next() == "22");
      expect(iter.next() == "333");
      expect(! iter.hasNext());
    }

    { BiFunction<GetLong, Long, Long> f = GetLong::get;
      expect(f.apply(new LongHolder(), 20L) == 20L);
    }

    { BiFunction<GetDouble, Double, Double> f = GetDouble::get;
      expect(f.apply(new DoubleHolder(), 20d) == 20d);
    }

    // This abort()s in machine.cpp
    // { Foo s = (Foo & Marker) this::requiresBridge;
    //   s.someFunction(1, 2, "");
    // }

    { UnboxedSerializable s = InvokeDynamic::addBoxed;
      expect(s.add(1, 2) == 3);
    }

    { Unboxed s = InvokeDynamic::addBoxed;
      expect(s.add(1, 2) == 3);
    }

    { Supplier<java.util.List<String>> s = java.util.ArrayList<String>::new;
      java.util.List<String> list = s.get();
    }
  }
}
