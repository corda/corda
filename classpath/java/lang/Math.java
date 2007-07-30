package java.lang;

public final class Math {
  private Math() { }

  public static int abs(int v) {
    return (v < 0 ? -v : v);
  }

  public static long abs(long v) {
    return (v < 0 ? -v : v);
  }

  public static float abs(float v) {
    return (v < 0 ? -v : v);
  }

  public static double abs(double v) {
    return (v < 0 ? -v : v);
  }

  public static long round(double v) {
    return (long) (v + 0.5);
  }

  public static int round(float v) {
    return (int) (v + 0.5);
  }

  public static native double floor(double v);

  public static native double ceil(double v);

  public static native double exp(double v);

  public static native double log(double v);

  public static native double cos(double v);

  public static native double sin(double v);

  public static native double tan(double v);

  public static native double acos(double v);

  public static native double asin(double v);

  public static native double atan(double v);

  public static native double sqrt(double v);

  public static native double pow(double v, double e);
}
