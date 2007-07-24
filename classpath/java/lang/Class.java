package java.lang;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

public final class Class <T> {
  private short flags;
  private byte vmFlags;
  private byte arrayDimensions;
  private short fixedSize;
  private short arrayElementSize;
  private int[] objectMask;
  private byte[] name;
  private Class super_;
  private Object[] interfaceTable;
  private Method[] virtualTable;
  private Field[] fieldTable;
  private Method[] methodTable;
  private Object[] staticTable;
  private Method initializer;

  private Class() { }

  public String getName() {
    return new String(name, 0, name.length - 1, false);
  }

  public static native Class forName(String name);

  public native boolean isAssignableFrom(Class c);

  public Field getDeclaredField(String name) throws NoSuchFieldException {
    for (int i = 0; i < fieldTable.length; ++i) {
      if (fieldTable[i].getName().equals(name)) {
        return fieldTable[i];
      }
    }

    throw new NoSuchFieldException(name);
  }

  private static boolean match(Class[] a, Class[] b) {
    if (a.length == b.length) {
      for (int i = 0; i < a.length; ++i) {
        if (! a[i].isAssignableFrom(b[i])) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  public Method getDeclaredMethod(String name, Class ... parameterTypes)
    throws NoSuchMethodException
  {
    for (int i = 0; i < methodTable.length; ++i) {
      if (methodTable[i].getName().equals(name)
          && match(parameterTypes, methodTable[i].getParameterTypes()))
      {
        return methodTable[i];
      }
    }

    throw new NoSuchMethodException(name);
  }
}
