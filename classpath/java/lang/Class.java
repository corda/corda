/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.GenericDeclaration;
import java.lang.annotation.Annotation;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.security.ProtectionDomain;

public final class Class <T> implements Type, GenericDeclaration {
  private static final int PrimitiveFlag = 1 << 4;

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
  private Object staticTable;
  private ClassLoader loader;

  private Class() { }

  public String toString() {
    return getName();
  }

  private static byte[] replace(int a, int b, byte[] s, int offset,
                                int length)
  {
    byte[] array = new byte[length];
    for (int i = 0; i < length; ++i) {
      byte c = s[i];
      array[i] = (byte) (c == a ? b : c);
    }
    return array;
  }

  public String getName() {
    return new String
      (replace('/', '.', name, 0, name.length - 1), 0, name.length - 1, false);
  }

  public Object staticTable() {
    return staticTable;
  }

  public T newInstance()
    throws IllegalAccessException, InstantiationException
  {
    try {
      return (T) getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static Class forName(String name) throws ClassNotFoundException {
    return forName
      (name, true, Method.getCaller().getDeclaringClass().getClassLoader());
  }

  public static Class forName(String name, boolean initialize,
                              ClassLoader loader)
    throws ClassNotFoundException
  {
    if (loader == null) {
      loader = Class.class.loader;
    }
    Class c = loader.loadClass(name);
    if (initialize) {
      c.initialize();
    }
    return c;
  }

  private static native Class primitiveClass(char name);

  private native void initialize();
  
  public static Class forCanonicalName(String name) {
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

  public Class getComponentType() {
    if (isArray()) {
      return (Class) staticTable;
    } else {
      return null;
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
      if (parameterTypes == null)
        parameterTypes = new Class[0];
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

  public Constructor getDeclaredConstructor(Class ... parameterTypes)
    throws NoSuchMethodException
  {
    Constructor c = null;
    Constructor[] constructors = getDeclaredConstructors();

    for (int i = 0; i < constructors.length; ++i) {
      if (match(parameterTypes, constructors[i].getParameterTypes())) {
        c = constructors[i];
      }
    }

    if (c == null) {
      throw new NoSuchMethodException();
    } else {
      return c;
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
      int ai = 0;
      for (int i = 0; i < fieldTable.length; ++i) {
        if (((fieldTable[i].getModifiers() & Modifier.PUBLIC)) != 0) {
          array[ai++] = fieldTable[i];
        }
      }
    }
    return array;
  }

  private int countMethods(boolean publicOnly) {
    int count = 0;
    if (methodTable != null) {
      for (int i = 0; i < methodTable.length; ++i) {
        if (((! publicOnly)
             || ((methodTable[i].getModifiers() & Modifier.PUBLIC)) != 0)
            && (! methodTable[i].getName().startsWith("<")))
        {
          ++ count;
        }
      }
    }
    return count;
  }

  public Method[] getDeclaredMethods() {
    Method[] array = new Method[countMethods(false)];
    if (methodTable != null) {
      int ai = 0;
      for (int i = 0; i < methodTable.length; ++i) {
        if (! methodTable[i].getName().startsWith("<")) {
          array[ai++] = methodTable[i];
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

  public T[] getEnumConstants() {
    if (Enum.class.isAssignableFrom(this)) {
      try {
        return (T[]) getMethod("values").invoke(null);
      } catch (Exception e) {
        throw new Error();
      }
    } else {
      return null;
    }
  }

  public ClassLoader getClassLoader() {
    return loader;
  }

  public int getModifiers() {
    return flags;
  }

  public boolean isInterface() {
    return (flags & Modifier.INTERFACE) != 0;
  }

  public Class getSuperclass() {
    return super_;
  }

  public boolean isArray() {
    return arrayElementSize != 0;
  }

  public boolean isInstance(Object o) {
    return o != null && isAssignableFrom(o.getClass());
  }

  public boolean isPrimitive() {
    return (vmFlags & PrimitiveFlag) != 0;
  }

  public URL getResource(String path) {
    if (! path.startsWith("/")) {
      String name = new String(this.name, 0, this.name.length - 1, false);
      int index = name.lastIndexOf('/');
      if (index >= 0) {
        path = name.substring(0, index) + "/" + path;
      }
    }
    return getClassLoader().getResource(path);
  }

  public InputStream getResourceAsStream(String path) {
    URL url = getResource(path);
    try {
      return (url == null ? null : url.openStream());
    } catch (IOException e) {
      return null;
    }
  }

  public boolean desiredAssertionStatus() {
    return false;
  }

  public T cast(Object o) {
    return (T) o;
  }

  public Object[] getSigners() {
    throw new UnsupportedOperationException();
  }

  public Annotation[] getDeclaredAnnotations() {
    throw new UnsupportedOperationException();
  }

  public boolean isEnum() {
    throw new UnsupportedOperationException();
  }

  public TypeVariable<Class<T>>[] getTypeParameters() {
    throw new UnsupportedOperationException();
  }

  public String getSimpleName() {
    throw new UnsupportedOperationException();
  }

  public Method getEnclosingMethod() {
    throw new UnsupportedOperationException();
  }

  public Constructor getEnclosingConstructor() {
    throw new UnsupportedOperationException();
  }

  public Class getEnclosingClass() {
    throw new UnsupportedOperationException();
  }

  public Class[] getDeclaredClasses() {
    throw new UnsupportedOperationException();
  }

  public <A extends Annotation> A getAnnotation(Class<A> c) {
    throw new UnsupportedOperationException();
  }

  public ProtectionDomain getProtectionDomain() {
    throw new UnsupportedOperationException();
  }

  // for GNU Classpath compatibility:
  void setSigners(Object[] signers) {
    throw new UnsupportedOperationException();
  }
}
