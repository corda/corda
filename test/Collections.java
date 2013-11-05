import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Collections {
  public static void main(String[] args) {
    testValues();
    testSort();
  }
  
  @SuppressWarnings("rawtypes")
  private static void testValues() {
    Map testMap = java.util.Collections.unmodifiableMap(java.util.Collections.emptyMap());
    Collection values = testMap.values();
    
    if (values == null) {
      throw new NullPointerException();
    }
    
    try {
      values.clear();
      
      throw new IllegalStateException("Object should be immutable, exception should have thrown");
    } catch (Exception e) {
      // expected
    }
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static <T extends Comparable<T>> void expectSorted(List<T> list) {
    for (int i = 1; i < list.size(); ++i) {
      expect(list.get(i - 1).compareTo(list.get(i)) <= 0);
    }
  }

  private static int pseudoRandom(int seed) {
    return 3170425 * seed + 132102;
  }

  private static <T extends Comparable<T>> int shuffle(List<T> list, int seed) {
    for (int i = list.size(); i > 1; --i) {
      int i2 = (seed < 0 ? -seed : seed) % i;
      T value = list.get(i - 1);
      list.set(i - 1, list.get(i2));
      list.set(i2, value);
      seed = pseudoRandom(seed);
    }
    return seed;
  }

  public static void testSort() {
    List<Integer> list = new ArrayList<Integer>();
    for (int i = 0; i < 64; ++i) {
      list.add(Integer.valueOf(i + 1));
    }
    ;
    int random = 12345;
    for (int i = 0; i < 32; ++i) {
      random = shuffle(list, random);
      java.util.Collections.sort(list);
      expectSorted(list);
    }
  }
}
