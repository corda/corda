package java.lang.reflect;

public final class Array {
  private Array() { }

  public static native Object get(Object array, int index);

  public static native void set(Object array, int index, Object value);

  public static native int getLength(Object array);

  private static native Object makeObjectArray(Class elementType, int length);

  public static Object newInstance(Class elementType, int length) {
    if (length < 0) {
      throw new NegativeArraySizeException();
    }

    if (elementType.equals(boolean.class)) {
      return new boolean[length];
    } else if (elementType.equals(byte.class)) {
      return new byte[length];
    } else if (elementType.equals(char.class)) {
      return new char[length];
    } else if (elementType.equals(short.class)) {
      return new short[length];
    } else if (elementType.equals(int.class)) {
      return new int[length];
    } else if (elementType.equals(long.class)) {
      return new long[length];
    } else if (elementType.equals(float.class)) {
      return new float[length];
    } else if (elementType.equals(double.class)) {
      return new double[length];
    } else {
      return makeObjectArray(elementType, length);
    }
  }
}
