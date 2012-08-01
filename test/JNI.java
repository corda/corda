public class JNI {
  static {
    System.loadLibrary("test");
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static native double addDoubles
    (double a1, double a2, double a3, double a4, double a5, double a6,
     double a7, double a8, double a9, double a10, double a11, double a12,
     double a13, double a14, double a15, double a16, double a17, double a18,
     double a19, double a20);

  private static native float addFloats
    (float a1, float a2, float a3, float a4, float a5, float a6,
     float a7, float a8, float a9, float a10, float a11, float a12,
     float a13, float a14, float a15, float a16, float a17, float a18,
     float a19, float a20);

  private static native double addMix
    (float a1, double a2, float a3, double a4, float a5, float a6,
     float a7, float a8, float a9, float a10, float a11, float a12,
     float a13, float a14, float a15, double a16, float a17, float a18,
     float a19, float a20);

  public static void main(String[] args) {
    expect(addDoubles
           (1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 6.0d, 7.0d, 8.0d, 9.0d, 10.0d, 11.0d,
            12.0d, 13.0d, 14.0d, 15.0d, 16.0d, 17.0d, 18.0d, 19.0d, 20.0d)
           == 210.0d);

    expect(addFloats
           (1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f,
            12.0f, 13.0f, 14.0f, 15.0f, 16.0f, 17.0f, 18.0f, 19.0f, 20.0f)
           == 210.0f);

    expect(addMix
           (1.0f, 2.0d, 3.0f, 4.0d, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f,
            12.0f, 13.0f, 14.0f, 15.0f, 16.0d, 17.0f, 18.0f, 19.0f, 20.0f)
           == 210.0d);
  }
}
