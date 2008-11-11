public class Longs {  
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
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

  private static long roundUp(long a, long b) {
    a += b - 1L;
    return a - (a % b);
  }

  public static void main(String[] args) {
    { long foo = 25214903884L;
      int radix = 10;
      expect(foo > 0);
      foo /= radix;
      expect(foo > 0);
    }

    expect(roundUp(156, 2) == 156);

    expect(Long.parseLong("25214903884") == 25214903884L);

    expect(Long.parseLong("-9223372036854775808") == -9223372036854775808L);

    expect(String.valueOf(25214903884L).equals("25214903884"));

    expect(String.valueOf(-9223372036854775808L).equals
           ("-9223372036854775808"));

    { long a = -5;
      long b = 2;
      expect(a >> b == -5L >> 2);
      expect(a >>> b == -5L >>> 2);
      expect(a << b == -5L << 2);
      expect(a + b == -5L + 2L);
      expect(a - b == -5L - 2L);
      expect(a * b == -5L * 2L);
      expect(a / b == -5L / 2L);
      expect(a % b == -5L % 2L);
      expect((a & b) == (-5L & 2L));
      expect((a | b) == (-5L | 2L));
      expect((a ^ b) == (-5L ^ 2L));
      expect(-a == 5L);
      expect(~a == ~-5L);

      a = 5;
      b = 2;
      expect(a >> b == 5L >> 2);
      expect(a >>> b == 5L >>> 2);
      expect(a << b == 5L << 2);
      expect(a + b == 5L + 2L);
      expect(a - b == 5L - 2L);
      expect(a * b == 5L * 2L);
      expect(a / b == 5L / 2L);
      expect(a % b == 5L % 2L);
      expect((a & b) == (5L & 2L));
      expect((a | b) == (5L | 2L));
      expect((a ^ b) == (5L ^ 2L));
      expect(-a == -5L);
      expect(~a == ~5L);
    }

    { long a = -25214903884L;
      long b = 2;
      expect(a >> b == -25214903884L >> 2);
      expect(a >>> b == -25214903884L >>> 2);
      expect(a << b == -25214903884L << 2);
      expect(a + b == -25214903884L + 2L);
      expect(a - b == -25214903884L - 2L);
      expect(a * b == -25214903884L * 2L);
      expect(a / b == -25214903884L / 2L);
      expect(a % b == -25214903884L % 2L);
      expect((a & b) == (-25214903884L & 2L));
      expect((a | b) == (-25214903884L | 2L));
      expect((a ^ b) == (-25214903884L ^ 2L));
      expect(-a == 25214903884L);
      expect(~a == ~-25214903884L);

      a = 25214903884L;
      b = 2;
      expect(a >> b == 25214903884L >> 2);
      expect(a >>> b == 25214903884L >>> 2);
      expect(a << b == 25214903884L << 2);
      expect(a + b == 25214903884L + 2L);
      expect(a - b == 25214903884L - 2L);
      expect(a * b == 25214903884L * 2L);
      expect(a / b == 25214903884L / 2L);
      expect(a % b == 25214903884L % 2L);
      expect((a & b) == (25214903884L & 2L));
      expect((a | b) == (25214903884L | 2L));
      expect((a ^ b) == (25214903884L ^ 2L));
      expect(-a == -25214903884L);
      expect(~a == ~25214903884L);
    }

    { long b = 2;
      expect((-25214903884L) >> b == -25214903884L >> 2);
      expect((-25214903884L) >>> b == -25214903884L >>> 2);
      expect((-25214903884L) << b == -25214903884L << 2);
      expect((-25214903884L) + b == -25214903884L + 2L);
      expect((-25214903884L) - b == -25214903884L - 2L);
      expect((-25214903884L) * b == -25214903884L * 2L);
      expect((-25214903884L) / b == -25214903884L / 2L);
      expect((-25214903884L) % b == -25214903884L % 2L);
      expect(((-25214903884L) & b) == (-25214903884L & 2L));
      expect(((-25214903884L) | b) == (-25214903884L | 2L));
      expect(((-25214903884L) ^ b) == (-25214903884L ^ 2L));

      b = 2;
      expect(25214903884L >> b == 25214903884L >> 2);
      expect(25214903884L >>> b == 25214903884L >>> 2);
      expect(25214903884L << b == 25214903884L << 2);
      expect(25214903884L + b == 25214903884L + 2L);
      expect(25214903884L - b == 25214903884L - 2L);
      expect(25214903884L * b == 25214903884L * 2L);
      expect(25214903884L / b == 25214903884L / 2L);
      expect(25214903884L % b == 25214903884L % 2L);
      expect((25214903884L & b) == (25214903884L & 2L));
      expect((25214903884L | b) == (25214903884L | 2L));
      expect((25214903884L ^ b) == (25214903884L ^ 2L));
    }

    { long a = 2L;
      expect(a + (-25214903884L) == 2L + (-25214903884L));
      expect(a - (-25214903884L) == 2L - (-25214903884L));
      expect(a * (-25214903884L) == 2L * (-25214903884L));
      expect(a / (-25214903884L) == 2L / (-25214903884L));
      expect(a % (-25214903884L) == 2L % (-25214903884L));
      expect((a & (-25214903884L)) == (2L & (-25214903884L)));
      expect((a | (-25214903884L)) == (2L | (-25214903884L)));
      expect((a ^ (-25214903884L)) == (2L ^ (-25214903884L)));

      a = 2L;
      expect(a + 25214903884L == 2L + 25214903884L);
      expect(a - 25214903884L == 2L - 25214903884L);
      expect(a * 25214903884L == 2L * 25214903884L);
      expect(a / 25214903884L == 2L / 25214903884L);
      expect(a % 25214903884L == 2L % 25214903884L);
      expect((a & 25214903884L) == (2L & 25214903884L));
      expect((a | 25214903884L) == (2L | 25214903884L));
      expect((a ^ 25214903884L) == (2L ^ 25214903884L));
    }

    { long x = 231;
      expect((x >> 32) == 0);
      expect((x >>> 32) == 0);
      expect((x << 32) == 992137445376L);

      int shift = 32;
      expect((x >> shift) == 0);
      expect((x >>> shift) == 0);
      expect((x << shift) == 992137445376L);

      long y = -231;
      expect((y >> 32) == 0xffffffffffffffffL);
      expect((y >>> 32) == 0xffffffffL);
    }

    expect(Long.valueOf(231L) == 231L);

    { byte[] array = new byte[8];
      putLong(231, array, 0);
      expect((array[0] & 0xff) == 0);
      expect((array[1] & 0xff) == 0);
      expect((array[2] & 0xff) == 0);
      expect((array[3] & 0xff) == 0);
      expect((array[4] & 0xff) == 0);
      expect((array[5] & 0xff) == 0);
      expect((array[6] & 0xff) == 0);
      expect((array[7] & 0xff) == 231);
    }

    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8);
    buffer.putLong(231);
    buffer.flip();
    expect(buffer.getLong() == 231);
  }

}
