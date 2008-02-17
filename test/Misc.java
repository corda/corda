public class Misc {
  private static int alpha;
  private static int beta;
  private static byte byte1, byte2, byte3;
  private int gamma;

  private String foo(String s) {
    return s;
  }

  public String bar(String s) {
    return s;
  }

  private static String baz(String s) {
    return s;
  }
  
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private synchronized byte sync() {
    byte[] array = new byte[123];
    return array[42];
  }

  private static synchronized byte syncStatic(boolean throw_) {
    byte[] array = new byte[123];
    if (throw_) {
      throw new RuntimeException();
    } else {
      return array[42];
    }
  }

  public static void putInt(int val, byte[] dst, int offset) {
    System.out.println("put " + val);
    dst[offset]   = (byte)((val >> 24) & 0xff);
    dst[offset+1] = (byte)((val >> 16) & 0xff);
    dst[offset+2] = (byte)((val >>  8) & 0xff);
    dst[offset+3] = (byte)((val      ) & 0xff);
  }

  public static void putLong(long val, byte[] dst, int offset) {
    putInt((int)(val >> 32), dst, offset);
    putInt((int)val, dst, offset + 4);
  }

  public String toString() {
    return super.toString();
  }

  public static void main(String[] args) {
//     byte2 = 0;
//     expect(byte2 == 0);

//     expect(Long.valueOf(231L) == 231L);

//     long x = 231;
//     expect((x >> 32) == 0);
//     expect((x >>> 32) == 0);
//     expect((x << 32) == 992137445376L);

//     long y = -231;
//     expect((y >> 32) == 0xffffffffffffffffL);
//     expect((y >>> 32) == 0xffffffffL);

//     byte[] array = new byte[8];
//     putLong(231, array, 0);
//     expect((array[0] & 0xff) == 0);
//     expect((array[1] & 0xff) == 0);
//     expect((array[2] & 0xff) == 0);
//     expect((array[3] & 0xff) == 0);
//     expect((array[4] & 0xff) == 0);
//     expect((array[5] & 0xff) == 0);
//     expect((array[6] & 0xff) == 0);
//     expect((array[7] & 0xff) == 231);

//     java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8);
//     buffer.putLong(231);
//     buffer.flip();
//     expect(buffer.getLong() == 231);

//     boolean v = Boolean.valueOf("true");

//     ClassLoader.getSystemClassLoader().toString();

//     int a = 2;
//     int b = 2;
//     int c = a + b;

    Misc m = new Misc();
//     m.toString();

//     String s = "hello";
//     m.foo(s);
//     m.bar(s);
//     baz(s);

//     m.sync();
//     syncStatic(false);
//     try {
//       syncStatic(true);
//     } catch (RuntimeException e) {
//       e.printStackTrace();
//     }

//     int d = alpha;
//     beta = 42;
//     alpha = 43;
//     int e = beta;
//     int f = alpha;
//     m.gamma = 44;

//     expect(beta == 42);
//     expect(alpha == 43);
//     expect(m.gamma == 44);
  }
}
