public class Arrays {
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
      java.util.Arrays.sort(array);
      expectSorted(array);
    }
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

      expect(java.util.Arrays.equals(a, b));
      a[0] = new Object();
      expect(! java.util.Arrays.equals(a, b));
      expect(! java.util.Arrays.equals(b, new Object[4]));
      expect(! java.util.Arrays.equals(a, null));
      expect(! java.util.Arrays.equals(null, b));
      expect(java.util.Arrays.equals((Object[])null, (Object[])null));
      b[0] = a[0];
      expect(java.util.Arrays.equals(a, b));

      java.util.Arrays.hashCode(a);
      java.util.Arrays.hashCode((Object[])null);
    }

    { String[] list = new String[] { "Hello", "World", "!" };
      Object[] result = java.util.Arrays.copyOf(list, 2, Object[].class);
      expect(list[1] == result[1]);
      expect(result.length == 2);
      expect(result.getClass().getComponentType() == Object.class);
    }

    testSort();
  }
}
