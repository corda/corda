import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.InvocationTargetException;

public class Reflection {
  public static boolean booleanMethod() {
    return true;
  }

  public static byte byteMethod() {
    return 1;
  }

  public static char charMethod() {
    return '2';
  }

  public static short shortMethod() {
    return 3;
  }

  public static int intMethod() {
    return 4;
  }

  public static float floatMethod() {
    return 5.0f;
  }

  public static long longMethod() {
    return 6;
  }

  public static double doubleMethod() {
    return 7.0;
  }

  public static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static class Hello<T> {
    private class World<S> { }
  }

  private static void innerClasses() throws Exception {
    Class c = Reflection.class;
    Class[] inner = c.getDeclaredClasses();
    expect(3 == inner.length);
    expect(Hello.class == inner[0]
           || Hello.class == inner[1]
           || Hello.class == inner[2]);
  }

  private int egads;

  private static void annotations() throws Exception {
    Field egads = Reflection.class.getDeclaredField("egads");
    expect(egads.getAnnotation(Deprecated.class) == null);
  }

  private Integer[] array;

  private Integer integer;

  public static Hello<Hello<Reflection>>.World<Hello<String>> pinky;

  private static void genericType() throws Exception {
    Field field = Reflection.class.getDeclaredField("egads");
    expect(field.getGenericType() == Integer.TYPE);

    field = Reflection.class.getField("pinky");
    expect("Reflection$Hello$World".equals(field.getType().getName()));
    expect(field.getGenericType() instanceof ParameterizedType);
    ParameterizedType type = (ParameterizedType) field.getGenericType();

    expect(type.getRawType() instanceof Class);
    Class<?> clazz = (Class<?>) type.getRawType();
    expect("Reflection$Hello$World".equals(clazz.getName()));

    expect(type.getOwnerType() instanceof ParameterizedType);
    ParameterizedType owner = (ParameterizedType) type.getOwnerType();
    clazz = (Class<?>) owner.getRawType();
    expect(clazz == Hello.class);

    Type[] args = type.getActualTypeArguments();
    expect(1 == args.length);
    expect(args[0] instanceof ParameterizedType);

    ParameterizedType arg = (ParameterizedType) args[0];
    expect(arg.getRawType() instanceof Class);
    clazz = (Class<?>) arg.getRawType();
    expect("Reflection$Hello".equals(clazz.getName()));

    args = arg.getActualTypeArguments();
    expect(1 == args.length);
    expect(args[0] == String.class);
  }

  public static void throwOOME() {
    throw new OutOfMemoryError();
  }

  public static void classType() throws Exception {
    // Class types
    expect(!Reflection.class.isAnonymousClass());
    expect(!Reflection.class.isLocalClass());
    expect(!Reflection.class.isMemberClass());

    expect(Reflection.Hello.class.isMemberClass());

    Cloneable anonymousLocal = new Cloneable() {};
    expect(anonymousLocal.getClass().isAnonymousClass());

    class NamedLocal {}
    expect(NamedLocal.class.isLocalClass());
  }

  private static class MyClassLoader extends ClassLoader {
    public Package definePackage1(String name) {
      return definePackage(name, null, null, null, null, null, null, null);
    }
  }

  public static void main(String[] args) throws Exception {
    expect(new MyClassLoader().definePackage1("foo").getName().equals("foo"));

    innerClasses();
    annotations();
    genericType();
    classType();

    Class system = Class.forName("java.lang.System");
    Field out = system.getDeclaredField("out");
    Class output = Class.forName("java.io.PrintStream");
    Method println = output.getDeclaredMethod("println", String.class);

    println.invoke(out.get(null), "Hello, World!");

    expect((Boolean) Reflection.class.getMethod("booleanMethod").invoke(null));

    expect(1 == (Byte) Reflection.class.getMethod("byteMethod").invoke(null));

    expect('2' == (Character) Reflection.class.getMethod
           ("charMethod").invoke(null));

    expect(3 == (Short) Reflection.class.getMethod
           ("shortMethod").invoke(null));

    expect(4 == (Integer) Reflection.class.getMethod
           ("intMethod").invoke(null));

    expect(5.0 == (Float) Reflection.class.getMethod
           ("floatMethod").invoke(null));

    expect(6 == (Long) Reflection.class.getMethod
           ("longMethod").invoke(null));

    expect(7.0 == (Double) Reflection.class.getMethod
           ("doubleMethod").invoke(null));

    { Class[][] array = new Class[][] { { Class.class } };
      expect("[Ljava.lang.Class;".equals(array[0].getClass().getName()));
      expect(Class[].class == array[0].getClass());
      expect(array.getClass().getComponentType() == array[0].getClass());
    }

    { Reflection r = new Reflection();
      expect(r.egads == 0);

      Reflection.class.getDeclaredField("egads").set(r, (Integer)42);
      expect(((Integer)Reflection.class.getDeclaredField("egads").get(r)) == 42);

      Reflection.class.getDeclaredField("egads").setInt(r, 43);
      expect(Reflection.class.getDeclaredField("egads").getInt(r) == 43);

      Integer[] array = new Integer[0];
      Reflection.class.getDeclaredField("array").set(r, array);
      expect(Reflection.class.getDeclaredField("array").get(r) == array);

      try {
        Reflection.class.getDeclaredField("array").set(r, new Object());
        expect(false);
      } catch (IllegalArgumentException e) {
        // cool
      }

      Integer integer = 45;
      Reflection.class.getDeclaredField("integer").set(r, integer);
      expect(Reflection.class.getDeclaredField("integer").get(r) == integer);

      try {
        Reflection.class.getDeclaredField("integer").set(r, new Object());
        expect(false);
      } catch (IllegalArgumentException e) {
        // cool
      }

      try {
        Reflection.class.getDeclaredField("integer").set
          (new Object(), integer);
        expect(false);
      } catch (IllegalArgumentException e) {
        // cool
      }

      try {
        Reflection.class.getDeclaredField("integer").get(new Object());
        expect(false);
      } catch (IllegalArgumentException e) {
        // cool
      }
    }

    try {
      Foo.class.getMethod("foo").invoke(null);
      expect(false);
    } catch (ExceptionInInitializerError e) {
      expect(e.getCause() instanceof MyException);
    }

    try {
      Foo.class.getConstructor().newInstance();
      expect(false);
    } catch (NoClassDefFoundError e) {
      // cool
    }

    try {
      Foo.class.getField("foo").get(null);
      expect(false);
    } catch (NoClassDefFoundError e) {
      // cool
    }

    try {
      Foo.class.getField("foo").set(null, (Integer)42);
      expect(false);
    } catch (NoClassDefFoundError e) {
      // cool
    }

    try {
      Foo.class.getField("foo").set(null, new Object());
      expect(false);
    } catch (IllegalArgumentException e) {
      // cool
    } catch (NoClassDefFoundError e) {
      // cool
    }

    { Method m = Reflection.class.getMethod("throwOOME");
      try {
        m.invoke(null);
      } catch(Throwable t) {
        expect(t.getClass() == InvocationTargetException.class);
      }
    }

    expect((Foo.class.getMethod("toString").getModifiers()
            & Modifier.PUBLIC) != 0);

    expect(avian.TestReflection.get(Baz.class.getField("foo"), new Baz())
           .equals(42));
    expect((Baz.class.getModifiers() & Modifier.PUBLIC) == 0);

    expect(B.class.getDeclaredMethods().length == 0);

    new Runnable() {
      public void run() {
        expect(getClass().getDeclaringClass() == null);
      }
    }.run();

    expect(avian.testing.annotations.Test.class.getPackage().getName().equals
           ("avian.testing.annotations"));

    expect(Baz.class.getField("foo").getAnnotation(Ann.class) == null);
    expect(Baz.class.getField("foo").getAnnotations().length == 0);

    expect(new Runnable() { public void run() { } }.getClass()
           .getEnclosingClass().equals(Reflection.class));

    expect(new Runnable() { public void run() { } }.getClass()
           .getEnclosingMethod().equals
           (Reflection.class.getMethod
            ("main", new Class[] { String[].class })));

    Slithy.class.getMethod("tove", Gybe.class);

    try {
      Slithy.class.getMethod("tove", Bandersnatch.class);
      expect(false);
    } catch (NoSuchMethodException e) {
      // cool
    }

    expect(C.class.getInterfaces().length == 1);
    expect(C.class.getInterfaces()[0].equals(B.class));
  }

  protected static class Baz {
    public int foo = 42;
  }
}

class Bandersnatch { }

class Gybe extends Bandersnatch { }

class Slithy {
  public static void tove(Gybe gybe) {
    // ignore
  }
}

class Foo {
  static {
    if (true) throw new MyException();
  }

  public Foo() { }

  public static int foo;

  public static void foo() {
    // ignore
  }
}

class MyException extends RuntimeException { }

interface A {
  void foo();
}

interface B extends A { }

class C implements B {
  public void foo() { }
}

@interface Ann { }
