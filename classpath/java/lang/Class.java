package java.lang;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
  private ClassLoader loader;

  private Class() { }

  public String getName() {
    return new String(name, 0, name.length - 1, false);
  }

  public static Class forName(String name) throws ClassNotFoundException {
    return forName
      (name, true, Method.getCaller().getDeclaringClass().getClassLoader());
  }

  public static Class forName(String name, boolean initialize,
                              ClassLoader loader)
    throws ClassNotFoundException
  {
    Class c = loader.loadClass(name);
    if (initialize) {
      c.initialize();
    }
    return c;
  }

  private static native Class primitiveClass(char name);

  private native void initialize();
  
  static Class forCanonicalName(String name) {
    try {
      if (name.startsWith("[")) {
        return forName(name);
      } else if (name.startsWith("L")) {
        return forName(name.substring(1, name.length() - 1));
      } else {
        if (name.length() == 1) {
          return primitiveClass(name.charAt(0));
        } else {
          throw new ClassNotFoundException(name);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public native boolean isAssignableFrom(Class c);

  private Field findField(String name) {
    if (fieldTable != null) {
      for (int i = 0; i < fieldTable.length; ++i) {
        if (fieldTable[i].getName().equals(name)) {
          return fieldTable[i];
        }
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
    if (methodTable != null) {
      for (int i = 0; i < methodTable.length; ++i) {
        if (methodTable[i].getName().equals(name)
            && match(parameterTypes, methodTable[i].getParameterTypes()))
        {
          return methodTable[i];
        }
      }
    }
    return null;
  }

  public Method getDeclaredMethod(String name, Class ... parameterTypes)
    throws NoSuchMethodException
  {
    if (name.startsWith("<")) {
      throw new NoSuchMethodException(name);
    }
    Method m = findMethod(name, parameterTypes);
    if (m == null) {
      throw new NoSuchMethodException(name);
    } else {
      return m;
    }
  }

  public Method getMethod(String name, Class ... parameterTypes)
    throws NoSuchMethodException
  {
    if (name.startsWith("<")) {
      throw new NoSuchMethodException(name);
    }
    for (Class c = this; c != null; c = c.super_) {
      Method m = c.findMethod(name, parameterTypes);
      if (m != null) {
        return m;
      }
    }
    throw new NoSuchMethodException(name);
  }

  public Constructor getConstructor(Class ... parameterTypes)
    throws NoSuchMethodException
  {
    Method m = findMethod("<init>", parameterTypes);
    if (m == null) {
      throw new NoSuchMethodException();
    } else {
      return new Constructor(m);
    }
  }

  private int countConstructors(boolean publicOnly) {
    int count = 0;
    if (methodTable != null) {
      for (int i = 0; i < methodTable.length; ++i) {
        if (((! publicOnly)
             || ((methodTable[i].getModifiers() & Modifier.PUBLIC)) != 0)
            && methodTable[i].getName().equals("<init>"))
        {
          ++ count;
        }
      }
    }
    return count;
  }

  public Constructor[] getDeclaredConstructors() {
    Constructor[] array = new Constructor[countConstructors(false)];
    if (methodTable != null) {
      int index = 0;
      for (int i = 0; i < methodTable.length; ++i) {
        if (methodTable[i].getName().equals("<init>")) {
          array[index++] = new Constructor(methodTable[i]);
        }
      }
    }

    return array;
  }

  public Constructor[] getConstructors() {
    Constructor[] array = new Constructor[countConstructors(true)];
    if (methodTable != null) {
      int index = 0;
      for (int i = 0; i < methodTable.length; ++i) {
        if (((methodTable[i].getModifiers() & Modifier.PUBLIC) != 0)
            && methodTable[i].getName().equals("<init>"))
        {
          array[index++] = new Constructor(methodTable[i]);
        }
      }
    }

    return array;
  }

  public Field[] getDeclaredFields() {
    if (fieldTable != null) {
      Field[] array = new Field[fieldTable.length];
      System.arraycopy(fieldTable, 0, array, 0, fieldTable.length);
      return array;
    } else {
      return new Field[0];
    }
  }

  private int countPublicFields() {
    int count = 0;
    if (fieldTable != null) {
      for (int i = 0; i < fieldTable.length; ++i) {
        if (((fieldTable[i].getModifiers() & Modifier.PUBLIC)) != 0) {
          ++ count;
        }
      }
    }
    return count;
  }

  public Field[] getFields() {
    Field[] array = new Field[countPublicFields()];
    if (fieldTable != null) {
      for (int i = 0; i < fieldTable.length; ++i) {
        if (((fieldTable[i].getModifiers() & Modifier.PUBLIC)) != 0) {
          array[i] = fieldTable[i];
        }
      }
    }
    return array;
  }

  private int countMethods(boolean publicOnly) {
    int count = 0;
    for (int i = 0; i < methodTable.length; ++i) {
      if (((! publicOnly)
           || ((methodTable[i].getModifiers() & Modifier.PUBLIC)) != 0)
          && (! methodTable[i].getName().startsWith("<")))
      {
        ++ count;
      }
    }
    return count;
  }

  public Method[] getDeclaredMethods() {
    Method[] array = new Method[countMethods(false)];
    if (methodTable != null) {
      int index = 0;
      for (int i = 0; i < methodTable.length; ++i) {
        if (! methodTable[i].getName().startsWith("<")) {
          array[index++] = methodTable[i];
        }
      }
    }

    return array;
  }

  public Method[] getMethods() {
    Method[] array = new Method[countMethods(true)];
    if (methodTable != null) {
      int index = 0;
      for (int i = 0; i < methodTable.length; ++i) {
        if (((methodTable[i].getModifiers() & Modifier.PUBLIC) != 0)
            && (! methodTable[i].getName().startsWith("<")))
        {
          array[index++] = methodTable[i];
        }
      }
    }

    return array;
  }

  public Class[] getInterfaces() {
    if (interfaceTable != null) {
      Class[] array = new Class[interfaceTable.length / 2];
      for (int i = 0; i < array.length; ++i) {
        array[i] = (Class) interfaceTable[i * 2];
      }
      return array;
    } else {
      return new Class[0];
    }
  }

  public ClassLoader getClassLoader() {
    return loader;
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
