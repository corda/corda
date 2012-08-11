import java.nio.ByteBuffer;

public class Buffers {
  static {
    System.loadLibrary("test");
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static void test(Factory factory1, Factory factory2) {
    { final int size = 64;
      ByteBuffer b1 = factory1.allocate(size);
      try {
        for (int i = 0; i < size; ++i)
          b1.put(i, (byte) 42);

        for (int i = 0; i < size; ++i)
          expect(b1.get(i) == 42);

        for (int i = 0; i < size / 2; ++i)
          b1.putShort(i * 2, (short) -12345);

        for (int i = 0; i < size / 2; ++i)
          expect(b1.getShort(i * 2) == -12345);

        for (int i = 0; i < size / 4; ++i)
          b1.putInt(i * 4, 0x12345678);

        for (int i = 0; i < size / 4; ++i)
          expect(b1.getInt(i * 4) == 0x12345678);

        for (int i = 0; i < size / 8; ++i)
          b1.putLong(i * 8, 0x1234567890ABCDEFL);

        for (int i = 0; i < size / 8; ++i)
          expect(b1.getLong(i * 8) ==  0x1234567890ABCDEFL);

        ByteBuffer b2 = factory2.allocate(size);
        try {
          b2.put(b1);

          for (int i = 0; i < size / 8; ++i)
            expect(b2.getLong(i * 8) ==  0x1234567890ABCDEFL);

        } finally {
          factory2.dispose(b2);
        }
      } finally {
        factory1.dispose(b1);
      }
    }
  }

  private static native ByteBuffer allocateNative(int capacity);

  private static native void freeNative(ByteBuffer b);

  public static void main(String[] args) {
    Factory array = new Factory() {
        public ByteBuffer allocate(int capacity) {
          return ByteBuffer.allocate(capacity);
        }

        public void dispose(ByteBuffer b) {
          // ignore
        }
      };

    Factory direct = new Factory() {
        public ByteBuffer allocate(int capacity) {
          return ByteBuffer.allocateDirect(capacity);
        }

        public void dispose(ByteBuffer b) {
          // ignore
        }
      };

    Factory native_ = new Factory() {
        public ByteBuffer allocate(int capacity) {
          return allocateNative(capacity);
        }

        public void dispose(ByteBuffer b) {
          freeNative(b);
        }
      };

    test(array, array);
    test(array, direct);
    test(array, native_);

    test(direct, array);
    test(direct, direct);
    test(direct, native_);

    test(native_, array);
    test(native_, direct);
    test(native_, native_);
  }

  private interface Factory {
    public ByteBuffer allocate(int capacity);

    public void dispose(ByteBuffer b);
  }
}
