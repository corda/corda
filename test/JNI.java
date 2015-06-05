import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class JNI {
  private static boolean onLoadCalled;

  static {
    System.loadLibrary("test");
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static float echo(float f) {
    return f;
  }

  private static native float doEcho(float f);

  private static double echo(double f) {
    return f;
  }

  private static native double doEcho(double f);

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

  private static native int addStackBoundary2
    (Object o1, Object o2, Object o3, int i1, int i2);

  private static native int addStackBoundary3
    (Object o1, Object o2, Object o3, int i1, int i2, int i3);

  private static native int addStackBoundary4
    (Object o1, Object o2, Object o3, int i1, int i2, int i3, int i4);

  private static native int addStackBoundary5
    (Object o1, Object o2, Object o3, int i1, int i2, int i3, int i4, int i5);

  private static native int addStackBoundary6
    (Object o1, Object o2, Object o3, int i1, int i2, int i3, int i4, int i5, int i6);

  private static native long fromReflectedMethod(Object m);

  private static native Object toReflectedMethod(Class c, long id,
                                                 boolean isStatic);

  private static native int callStaticIntMethod(Class c, long id);

  private static native Object newObject(Class c, long id);

  private static native long fromReflectedField(Field f);

  private static native Field toReflectedField(Class c, long id,
                                               boolean isStatic);

  private static native int getStaticIntField(Class c, long id);

  private static native Object testLocalRef(Object o);

  public static int method242() { return 242; }
  
  public static final int field950 = 950;

  public static void main(String[] args) throws Exception {
    expect(onLoadCalled);

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

    expect(addStackBoundary2(null, null, null, 1, 10) == 11);
    expect(addStackBoundary3(null, null, null, 1, 10, 100) == 111);
    expect(addStackBoundary4(null, null, null, 1, 10, 100, 1000) == 1111);
    expect(addStackBoundary5(null, null, null, 1, 10, 100, 1000, 10000) == 11111);
    expect(addStackBoundary6(null, null, null, 1, 10, 100, 1000, 10000, 100000) == 111111);

    expect(doEcho(42.0f) == 42.0f);
    expect(doEcho(42.0d) == 42.0d);

    expect(callStaticIntMethod
           (JNI.class, fromReflectedMethod
            (JNI.class.getMethod("method242"))) == 242);

    expect(((Method) toReflectedMethod
            (JNI.class, fromReflectedMethod
             (JNI.class.getMethod("method242")), true))
           .getName().equals("method242"));

    expect(newObject
           (JNI.class, fromReflectedMethod
            (JNI.class.getConstructor())) instanceof JNI);

    expect(((Constructor) toReflectedMethod
            (JNI.class, fromReflectedMethod
             (JNI.class.getConstructor()), false))
           .getDeclaringClass().equals(JNI.class));

    expect(getStaticIntField
           (JNI.class, fromReflectedField
            (JNI.class.getField("field950"))) == 950);

    expect(toReflectedField
           (JNI.class, fromReflectedField
            (JNI.class.getField("field950")), true)
           .getName().equals("field950"));

    { Object o = new Object();
      expect(testLocalRef(o) == o);
    }
  }
}
