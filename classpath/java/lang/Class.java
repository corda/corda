package java.lang;

import java.lang.reflect.Constructor;
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

  private Field findField(String name) {
    for (int i = 0; i < fieldTable.length; ++i) {
      if (fieldTable[i].getName().equals(name)) {
        return fieldTable[i];
      }
    }
    return null;
  }

  public Field getDeclaredField(String name) throws NoSuchFieldException {
    Field f = findField(name);
    if (f == null) {
      throw new NoSuchFieldException(name);
    } else {
      return f;
    }
  }

  public Field getField(String name) throws NoSuchFieldException {
    for (Class c = this; c != null; c = c.super_) {
      Field f = c.findField(name);
      if (f != null) {
        return f;
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

  private Method findMethod(String name, Class[] parameterTypes) {
    for (int i = 0; i < methodTable.length; ++i) {
      if (methodTable[i].getName().equals(name)
          && match(parameterTypes, methodTable[i].getParameterTypes()))
      {
        return methodTable[i];
      }
    }
    return null;
  }

  public Method getDeclaredMethod(String name, Class ... parameterTypes)
    throws NoSuchMethodException
  {
    Method f = findMethod(name, parameterTypes);
    if (f == null) {
      throw new NoSuchMethodException(name);
    } else {
      return f;
    }
  }

  public Method getMethod(String name, Class ... parameterTypes)
    throws NoSuchMethodException
  {
    for (Class c = this; c != null; c = c.super_) {
      Method f = c.findMethod(name, parameterTypes);
      if (f != null) {
        return f;
      }
    }
    throw new NoSuchMethodException(name);
  }

  public Constructor getConstructor(Class ... parameterTypes)
    throws NoSuchMethodException
  {
    return new Constructor(getDeclaredMethod("<init>", parameterTypes));
  }

  public Constructor[] getConstructors() {
    int count = 0;
    for (int i = 0; i < methodTable.length; ++i) {
      if (methodTable[i].getName().equals("<init>")) {
        ++ count;
      }
    }

    Constructor[] array = new Constructor[count];
    int index = 0;
    for (int i = 0; i < methodTable.length; ++i) {
      if (methodTable[i].getName().equals("<init>")) {
        array[index++] = new Constructor(methodTable[i]);
      }
    }

    return array;
  }

  public Constructor[] getDeclaredConstructors() {
    return getConstructors();
  }

  public Field[] getDeclaredFields() {
    Field[] array = new Field[fieldTable.length];
    System.arraycopy(fieldTable, 0, array, 0, fieldTable.length);
    return array;
  }

  public Method[] getDeclaredMethods() {
    int count = 0;
    for (int i = 0; i < methodTable.length; ++i) {
      if (! methodTable[i].getName().equals("<init>")) {
        ++ count;
      }
    }

    Method[] array = new Method[count];
    int index = 0;
    for (int i = 0; i < methodTable.length; ++i) {
      if (! methodTable[i].getName().equals("<init>")) {
        array[index++] = methodTable[i];
      }
    }

    return array;
  }

  public Class[] getInterfaces() {
    Class[] array = new Class[interfaceTable.length / 2];
    for (int i = 0; i < array.length; ++i) {
      array[i] = (Class) interfaceTable[i * 2];
    }
    return array;
  }

  public ClassLoader getClassLoader() {
    return ClassLoader.getSystemClassLoader();
  }

  public int getModifiers() {
    return flags;
  }

  public Class getSuperclass() {
    return super_;
  }

  public boolean isArray() {
    return arrayElementSize != 0;
  }

  public boolean isInstance(Object o) {
    return isAssignableFrom(o.getClass());
  }

  public boolean isPrimitive() {
    return equals(boolean.class)
      || equals(byte.class)
      || equals(char.class)
      || equals(short.class)
      || equals(int.class)
      || equals(long.class)
      || equals(float.class)
      || equals(double.class)
      || equals(void.class);
  }
}
