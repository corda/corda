public class Floats {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static double multiply(double a, double b) {
    return a * b;
  }

  private static float multiply(float a, float b) {
    return a * b;
  }

  private static double divide(double a, double b) {
    return a / b;
  }

  private static double subtract(double a, double b) {
    return a - b;
  }

  private double field = 100d;

  private static int doubleToInt(Floats f) {
    return (int) f.field;
  }

  public static void main(String[] args) {
    expect(multiply(0.5d, 0.5d) == 0.25d);
    expect(multiply(0.5f, 0.5f) == 0.25f);

    expect(multiply(0.5d, 0.1d) == 0.05d);
    expect(multiply(0.5f, 0.1f) == 0.05f);

    expect(multiply(0.5d, 0.5d) < 0.5d);
    expect(multiply(0.5f, 0.5f) < 0.5f);

    expect(multiply(0.5d, 0.1d) < 0.5d);
    expect(multiply(0.5f, 0.1f) < 0.5f);

    expect(multiply(0.5d, 0.5d) > 0.1d);
    expect(multiply(0.5f, 0.5f) > 0.1f);

    expect(multiply(0.5d, 0.1d) > 0.01d);
    expect(multiply(0.5f, 0.1f) > 0.01f);

    expect(divide(0.5d, 0.5d) == 1.0d);

    expect(divide(0.5d, 0.1d) == 5.0d);

    expect(subtract(0.5d, 0.5d) == 0.0d);

    expect(subtract(0.5d, 0.1d) == 0.4d);

    { double d = 1d;
      expect(((int) d) == 1);
    }

    { double d = 12345d;
      expect(((int) d) == 12345);
    }

    expect(doubleToInt(new Floats()) == 100);

    { float f = 1f;
      expect(((int) f) == 1);
    }

    { float f = 1f;
      expect(((long) f) == 1);
    }

    expect(Math.round(0.4f) == 0);
    expect(Math.round(0.5f) == 1);
    expect(Math.round(1.0f) == 1);
    expect(Math.round(1.9f) == 2);

    expect(Math.round(0.4d) == 0);
    expect(Math.round(0.5d) == 1);
    expect(Math.round(1.0d) == 1);
    expect(Math.round(1.9d) == 2);

    { float b = 1.0f;
      int blue = (int)(b * 255 + 0.5);
      expect(blue == 255);
    }

    { long z = 6553311036568663L;
      double d = (double) z;
      expect(d == 6553311036568663.0);
    }

    { long z = 12345L;
      float f = (float) z;
      expect(f == 12345.0);
    }

    { int z = 12345;
      float f = (float) z;
      expect(f == 12345.0);
    }

    { int z = 12345;
      double d = (double) z;
      expect(d == 12345.0);
    }
  }
}
