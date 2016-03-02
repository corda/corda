import sun.misc.Unsafe;

public class UnsafeTest {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static void unsafeThrow(Unsafe u) {
    u.throwException(new Exception());
  }

  private static void unsafeCatch(Unsafe u) {
    boolean success = false;
    try {
      unsafeThrow(u);
    } catch(Exception e) {
      expect(e.getClass() == Exception.class);
      success = true;
    }
    expect(success);
  }

  private static void unsafeMemory(Unsafe u) {
    final long size = 64;
    long memory = u.allocateMemory(size);
    try {
      for (int i = 0; i < size; ++i)
        u.putByte(memory + i, (byte) 42);

      for (int i = 0; i < size; ++i)
        expect(u.getByte(memory + i) == 42);

      for (int i = 0; i < size / 2; ++i)
        u.putShort(memory + (i * 2), (short) -12345);

      for (int i = 0; i < size / 2; ++i)
        expect(u.getShort(memory + (i * 2)) == -12345);

      for (int i = 0; i < size / 2; ++i)
        u.putChar(memory + (i * 2), (char) 23456);

      for (int i = 0; i < size / 2; ++i)
        expect(u.getChar(memory + (i * 2)) == 23456);

      for (int i = 0; i < size / 4; ++i)
        u.putInt(memory + (i * 4), 0x12345678);

      for (int i = 0; i < size / 4; ++i)
        expect(u.getInt(memory + (i * 4)) == 0x12345678);

      for (int i = 0; i < size / 4; ++i)
        u.putFloat(memory + (i * 4), 1.2345678F);

      for (int i = 0; i < size / 4; ++i)
        expect(u.getFloat(memory + (i * 4)) == 1.2345678F);

      for (int i = 0; i < size / 8; ++i)
        u.putLong(memory + (i * 8), 0x1234567890ABCDEFL);

      for (int i = 0; i < size / 8; ++i)
        expect(u.getLong(memory + (i * 8)) ==  0x1234567890ABCDEFL);

      for (int i = 0; i < size / 8; ++i)
        u.putDouble(memory + (i * 8), 1.23456789012345D);

      for (int i = 0; i < size / 8; ++i)
        expect(u.getDouble(memory + (i * 8)) ==  1.23456789012345D);

      for (int i = 0; i < size / 8; ++i)
        u.putAddress(memory + (i * 8), 0x12345678);

      for (int i = 0; i < size / 8; ++i)
        expect(u.getAddress(memory + (i * 8)) == 0x12345678);
    } finally {
      u.freeMemory(memory);
    }
  }

  private static void unsafeArray(Unsafe u) {
    final int offset = u.arrayBaseOffset(long[].class);
    final int scale = u.arrayIndexScale(long[].class);
    final int size = 64;
    final long[] array = new long[size];

    for (int i = 0; i < size; ++i)
      u.putBooleanVolatile(array, offset + (i * scale), i % 2 == 0);

    for (int i = 0; i < size; ++i)
      expect(u.getBooleanVolatile(array, offset + (i * scale))
             == (i % 2 == 0));

    for (int i = 0; i < size; ++i)
      u.putByteVolatile(array, offset + (i * scale), (byte) 42);

    for (int i = 0; i < size; ++i)
      expect(u.getByteVolatile(array, offset + (i * scale)) == 42);

    for (int i = 0; i < size; ++i)
      u.putShortVolatile(array, offset + (i * scale), (short) -12345);

    for (int i = 0; i < size; ++i)
      expect(u.getShortVolatile(array, offset + (i * scale)) == -12345);

    for (int i = 0; i < size; ++i)
      u.putCharVolatile(array, offset + (i * scale), (char) 23456);

    for (int i = 0; i < size; ++i)
      expect(u.getCharVolatile(array, offset + (i * scale)) == 23456);

    for (int i = 0; i < size; ++i)
      u.putIntVolatile(array, offset + (i * scale), 0x12345678);

    for (int i = 0; i < size; ++i)
      expect(u.getIntVolatile(array, offset + (i * scale)) == 0x12345678);

    for (int i = 0; i < size; ++i)
      u.putFloatVolatile(array, offset + (i * scale), 1.2345678F);

    for (int i = 0; i < size; ++i)
      expect(u.getFloatVolatile(array, offset + (i * scale)) == 1.2345678F);

    for (int i = 0; i < size; ++i)
      u.putLongVolatile(array, offset + (i * scale), 0x1234567890ABCDEFL);

    for (int i = 0; i < size; ++i)
      expect(u.getLongVolatile(array, offset + (i * scale))
             == 0x1234567890ABCDEFL);

    for (int i = 0; i < size; ++i)
      u.putDoubleVolatile(array, offset + (i * scale), 1.23456789012345D);

    for (int i = 0; i < size; ++i)
      expect(u.getDoubleVolatile(array, offset + (i * scale))
             == 1.23456789012345D);
  }

  private static class Data {
    public long longField;
    public double doubleField;
  }

  private static void unsafeObject(Unsafe u) throws Exception {
    final long longOffset = u.objectFieldOffset
      (Data.class.getField("longField"));

    final long doubleOffset = u.objectFieldOffset
      (Data.class.getField("doubleField"));

    Data data = new Data();

    u.putLong(data, longOffset, 0x1234567890ABCDEFL);

    u.putDouble(data, doubleOffset, 1.23456789012345D);

    expect(u.getLong(data, longOffset) == 0x1234567890ABCDEFL);

    expect(u.getDouble(data, doubleOffset) == 1.23456789012345D);
  }

  public static void main(String[] args) throws Exception {
    System.out.println("method count is "
                       + Unsafe.class.getDeclaredMethods().length);

    Unsafe u = avian.Machine.getUnsafe();

    unsafeCatch(u);
    unsafeMemory(u);
    unsafeArray(u);
    unsafeObject(u);
  }
}
