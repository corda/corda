import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;
import static avian.testing.Asserts.*;

public class Buffers {
  static {
    System.loadLibrary("test");
  }
  
  private static void testArrays(Factory factory1, Factory factory2) throws UnsupportedEncodingException {
    final int size = 64;
    ByteBuffer b1 = factory1.allocate(size);
    ByteBuffer b2 = factory2.allocate(size);
    
    String s = "1234567890abcdefghijklmnopqrstuvwxyz";
    b1.put(s.getBytes("UTF-8"));
    b1.flip();
    byte[] ba = new byte[s.length()];
    b1.get(ba);
    assertEquals(s, new String(ba, "UTF-8"));
    b1.position(0);
    b2.put(b1);
    b2.flip();
    b2.get(ba);
    assertEquals(s, new String(ba, "UTF-8"));
    b1.position(0);
    b2.position(0);
    b1.limit(b1.capacity());
    b2.limit(b2.capacity());
    b1.put(s.getBytes("UTF-8"), 4, 5);
    b1.flip();
    ba = new byte[5];
    b1.get(ba);
    assertEquals(s.substring(4, 9), new String(ba, "UTF-8"));
  }

  private static void testPrimativeGetAndSet(Factory factory1, Factory factory2) {
    { final int size = 64;
      ByteBuffer b1 = factory1.allocate(size);
      try {

        for (int i = 0; i < size; ++i)
          b1.put(i, (byte) 42);

        for (int i = 0; i < size; ++i)
          assertEquals(b1.get(i), 42);
        
        for (int i = 0; i < size/4; i++) 
          b1.putFloat(i*4, (float) 19.12);
        
        for (int i = 0; i < size/4; i++) 
          assertEquals(b1.getFloat(i*4), (float) 19.12);

        ByteBuffer b3 = b1.duplicate();
        for (int i = 0; i < size/4; i++)
          assertEquals(b3.getFloat(), (float) 19.12);
        assertEquals(64, b3.position());
        
        for (int i = 0; i < size/8; i++) 
          b1.putDouble(i*8, (double) 19.12);
        
        for (int i = 0; i < size/8; i++)
          assertEquals(b1.getDouble(i*8), (double) 19.12);
        
        b3.position(0);
        
        for (int i = 0; i < size/8; i++)
          assertEquals(b3.getDouble(i*8), (double) 19.12);

        for (int i = 0; i < size / 2; ++i)
          b1.putShort(i * 2, (short) -12345);

        for (int i = 0; i < size / 2; ++i)
          assertEquals(b1.getShort(i * 2), -12345);

        for (int i = 0; i < size / 4; ++i)
          b1.putInt(i * 4, 0x12345678);

        for (int i = 0; i < size / 4; ++i)
          assertEquals(b1.getInt(i * 4), 0x12345678);

        for (int i = 0; i < size / 8; ++i)
          b1.putLong(i * 8, 0x1234567890ABCDEFL);

        for (int i = 0; i < size / 8; ++i)
          assertEquals(b1.getLong(i * 8),  0x1234567890ABCDEFL);

        ByteBuffer b2 = factory2.allocate(size);
        try {
          b2.put(b1);

          for (int i = 0; i < size / 8; ++i)
            assertTrue(b2.getLong(i * 8) ==  0x1234567890ABCDEFL);

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

  public static void main(String[] args) throws Exception {
    Factory array = new Factory() {
        public ByteBuffer allocate(int capacity) {
          return ByteBuffer.allocate(capacity);
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

    testPrimativeGetAndSet(array, array);
    testArrays(array, array);
    testPrimativeGetAndSet(array, native_);
    testArrays(array, native_);

    testPrimativeGetAndSet(native_, array);
    testArrays(native_, array);
    testPrimativeGetAndSet(native_, native_);
    testArrays(native_, native_);

    try {
      ByteBuffer.allocate(1).getInt();
      assertTrue(false);
    } catch (BufferUnderflowException e) {
      // cool
    }

    try {
      ByteBuffer.allocate(1).getInt(0);
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
      // cool
    }

    try {
      ByteBuffer.allocate(1).putInt(1);
      assertTrue(false);
    } catch (BufferOverflowException e) {
      // cool
    }

    try {
      ByteBuffer.allocate(1).putInt(0, 1);
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
      // cool
    }
  }

  private interface Factory {
    public ByteBuffer allocate(int capacity);

    public void dispose(ByteBuffer b);
  }
}
