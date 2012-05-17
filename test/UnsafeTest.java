import sun.misc.Unsafe;

public class UnsafeTest {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    Unsafe u = avian.Machine.getUnsafe();

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
}
