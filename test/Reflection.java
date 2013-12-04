import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
    expect(1 == inner.length);
    expect(Hello.class == inner[0]);
  }

  private int egads;

  private static void annotations() throws Exception {
    Field egads = Reflection.class.getDeclaredField("egads");
    expect(egads.getAnnotation(Deprecated.class) == null);
  }

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

  public static void main(String[] args) throws Exception {
    innerClasses();
    annotations();
    genericType();

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

    Class[][] array = new Class[][] { { Class.class } };
    expect("[Ljava.lang.Class;".equals(array[0].getClass().getName()));
    expect(Class[].class == array[0].getClass());
    expect(array.getClass().getComponentType() == array[0].getClass());
  }
}
