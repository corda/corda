public class Integers {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    { int a = 2;
      int b = 2;
      int c = a + b;
    }

    { int a = -5;
      int b = 2;
      expect(a >> b == -5 >> 2);
      expect(a >>> b == -5 >>> 2);
      expect(a << b == -5 << 2);
      expect(a + b == -5 + 2);
      expect(a - b == -5 - 2);
      expect(a * b == -5 * 2);
      expect(a / b == -5 / 2);
      expect(a % b == -5 % 2);
      expect((a & b) == (-5 & 2));
      expect((a | b) == (-5 | 2));
      expect((a ^ b) == (-5 ^ 2));
      expect(-a == 5);
      expect(~a == ~-5);

      a = 5;
      b = 2;
      expect(a >> b == 5 >> 2);
      expect(a >>> b == 5 >>> 2);
      expect(a << b == 5 << 2);
      expect(a + b == 5 + 2);
      expect(a - b == 5 - 2);
      expect(a * b == 5 * 2);
      expect(a / b == 5 / 2);
      expect(a % b == 5 % 2);
      expect((a & b) == (5 & 2));
      expect((a | b) == (5 | 2));
      expect((a ^ b) == (5 ^ 2));
      expect(-a == -5);
      expect(~a == ~5);
    }

    { int get_buffer = 2144642881;
      int bits_left = 30;
      int l = 9;
      int code = (((get_buffer >> (bits_left -= (l)))) & ((1<<(l))-1));
      expect(code == 510);
    }

    { int width = 8;
      int height = 8;
      int depth = 24;
      int scanlinePad = 4;

      int bytesPerLine = (((width * depth + 7) / 8) + (scanlinePad - 1))
        / scanlinePad * scanlinePad;
      expect(bytesPerLine == 24);
    }
  }
}
