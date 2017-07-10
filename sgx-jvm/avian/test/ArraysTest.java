import java.util.Arrays;

public class ArraysTest {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static <T extends Comparable<T>> void expectSorted(T[] array) {
    for (int i = 1; i < array.length; ++i) {
      expect(array[i - 1].compareTo(array[i]) <= 0);
    }
  }

  private static int pseudoRandom(int seed) {
    return 3170425 * seed + 132102;
  }

  private static <T extends Comparable<T>> int shuffle(T[] array, int seed) {
    for (int i = array.length; i > 1; --i) {
      int i2 = (seed < 0 ? -seed : seed) % i;
      T value = array[i - 1];
      array[i - 1] = array[i2];
      array[i2] = value;
      seed = pseudoRandom(seed);
    }
    return seed;
  }

  public static void testSort() {
    Integer[] array = new Integer[64];
    for (int i = 0; i < array.length; ++i) {
      array[i] = Integer.valueOf(i + 1);
    }
    ;
    int random = 12345;
    for (int i = 0; i < 32; ++i) {
      random = shuffle(array, random);
      Arrays.sort(array);
      expectSorted(array);
    }
  }

  public static void testBinarySearch() {
    int[] a = new int[]{ 1, 5, 7, 8, 10, 13, 17, 18, 21, 26 };

    int i = Arrays.binarySearch(a, 5);
    expect(i == 1);
    i = Arrays.binarySearch(a, 17);
    expect(i == 6);
    i = Arrays.binarySearch(a, 6);
    expect(i == -3);
    i = Arrays.binarySearch(a, 1, 4, 8);
    expect(i == 3);
    i = Arrays.binarySearch(a, 1, 4, 10);
    expect(i == -5);

    Exception exception = null;
    try {
      Arrays.binarySearch(a, -1, a.length, 4);
    } catch (ArrayIndexOutOfBoundsException e) {
      exception = e;
    }
    expect(exception != null);

    exception = null;
    try {
      Arrays.binarySearch(a, 0, a.length + 1, 4);
    } catch (ArrayIndexOutOfBoundsException e) {
      exception = e;
    }
    expect(exception != null);
  }

  public static void main(String[] args) {
    { int[] array = new int[0];
      Exception exception = null;
      try {
        int x = array[0];
      } catch (ArrayIndexOutOfBoundsException e) {
        exception = e;
      }

      expect(exception != null);
    }

    { int[] array = new int[0];
      Exception exception = null;
      try {
        int x = array[-1];
      } catch (ArrayIndexOutOfBoundsException e) {
        exception = e;
      }

      expect(exception != null);
    }

    { int[] array = new int[3];
      int i = 0;
      array[i++] = 1;
      array[i++] = 2;
      array[i++] = 3;

      expect(array[--i] == 3);
      expect(array[--i] == 2);
      expect(array[--i] == 1);
    }

    { Object[][] array = new Object[1][1];
      expect(array.length == 1);
      expect(array[0].length == 1);
    }

    { Object[][] array = new Object[2][3];
      expect(array.length == 2);
      expect(array[0].length == 3);
    }

    { int j = 0;
      byte[] decodeTable = new byte[256];
      for (int i = 'A'; i <= 'Z'; ++i) decodeTable[i] = (byte) j++;
      for (int i = 'a'; i <= 'z'; ++i) decodeTable[i] = (byte) j++;
      for (int i = '0'; i <= '9'; ++i) decodeTable[i] = (byte) j++;
      decodeTable['+'] = (byte) j++;
      decodeTable['/'] = (byte) j++;
      decodeTable['='] = 0;

      expect(decodeTable['a'] != 0);
    }

    { boolean p = true;
      int[] array = new int[] { 1, 2 };
      expect(array[0] == array[p ? 0 : 1]);
      p = false;
      expect(array[1] == array[p ? 0 : 1]);
    }

    { int[] array = new int[1024];
      array[1023] = -1;
      expect(array[1023] == -1);
      expect(array[1022] == 0);
    }

    { Integer[] array = (Integer[])
        java.lang.reflect.Array.newInstance(Integer.class, 1);
      array[0] = Integer.valueOf(42);
      expect(array[0].intValue() == 42);
    }

    { Object[] a = new Object[3];
      Object[] b = new Object[3];

      expect(Arrays.equals(a, b));
      a[0] = new Object();
      expect(! Arrays.equals(a, b));
      expect(! Arrays.equals(b, new Object[4]));
      expect(! Arrays.equals(a, null));
      expect(! Arrays.equals(null, b));
      expect(Arrays.equals((Object[])null, (Object[])null));
      b[0] = a[0];
      expect(Arrays.equals(a, b));

      Arrays.hashCode(a);
      Arrays.hashCode((Object[])null);
    }

    { String[] list = new String[] { "Hello", "World", "!" };
      Object[] result = Arrays.copyOf(list, 2, Object[].class);
      expect(list[1] == result[1]);
      expect(result.length == 2);
      expect(result.getClass().getComponentType() == Object.class);
    }

    { Object[] a = new Object[3];
      Object[] b = new Object[3];

      expect(Arrays.deepEquals(a, b));

      a[0] = new Object();
      expect(! Arrays.deepEquals(a, b));
      expect(! Arrays.deepEquals(b, new Object[4]));
      expect(! Arrays.deepEquals(a, null));
      expect(! Arrays.deepEquals(null, b));
      expect(Arrays.deepEquals((Object[])null, (Object[])null));

      b[0] = a[0];
      expect(Arrays.deepEquals(a, b));

      a[0] = new Object[] {1};
      expect(! Arrays.deepEquals(a, b));
      b[0] = new Object[] {1};
      expect(Arrays.deepEquals(a, b));
      ((Object[])a[0])[0] = (Long)1L;
      expect(! Arrays.deepEquals(a, b));
      a[0] = new Integer[] {1};
      expect(Arrays.deepEquals(a, b));

      a[0] = new int[] {1};
      expect(! Arrays.deepEquals(a, b));
      b[0] = new int[] {1};
      expect(Arrays.deepEquals(a, b));
    }

    testSort();
    testBinarySearch();
  }
}
