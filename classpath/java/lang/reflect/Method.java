/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

public class Method<T> extends AccessibleObject
  implements Member, GenericDeclaration
{
  private byte vmFlags;
  private byte returnCode;
  private byte parameterCount;
  private byte parameterFootprint;
  private short flags;
  private short offset;
  private int nativeID;
  private byte[] name;
  private byte[] spec;
  private Class<T> class_;
  private Object code;
  private long compiled;

  private Method() { }

  public boolean isAccessible() {
    return (vmFlags & Accessible) != 0;
  }

  public void setAccessible(boolean v) {
    if (v) vmFlags |= Accessible; else vmFlags &= ~Accessible;
  }

  public static native Method getCaller();

  public Class<T> getDeclaringClass() {
    return class_;
  }

  public int getModifiers() {
    return flags;
  }

  public String getName() {
    return new String(name, 0, name.length - 1, false);
  }

  private static int next(char c, String s, int start) {
    for (int i = start; i < s.length(); ++i) {
      if (s.charAt(i) == c) return i;
    }
    throw new RuntimeException();
  }

  public Class[] getParameterTypes() {
    int count = parameterCount;

    Class[] types = new Class[count];
    int index = 0;

    String spec = new String(this.spec, 1, this.spec.length - 1, false);

    try {
      for (int i = 0; i < spec.length(); ++i) {
        char c = spec.charAt(i);
        if (c == ')') {
          break;
        } else if (c == 'L') {
          int start = i + 1;
          i = next(';', spec, start);
          String name = spec.substring(start, i).replace('/', '.');
          types[index++] = Class.forName(name, true, class_.getClassLoader());
        } else if (c == '[') {
          int start = i;
          while (spec.charAt(i) == '[') ++i;

          if (spec.charAt(i) == 'L') {
            i = next(';', spec, i + 1);
            String name = spec.substring(start, i).replace('/', '.');
            types[index++] = Class.forName
              (name, true, class_.getClassLoader());
          } else {
            String name = spec.substring(start, i + 1);
            types[index++] = Class.forCanonicalName
              (class_.getClassLoader(), name);
          }
        } else {
          String name = spec.substring(i, i + 1);
          types[index++] = Class.forCanonicalName
            (class_.getClassLoader(), name);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    return types;
  }

  public Object invoke(Object instance, Object ... arguments)
    throws InvocationTargetException, IllegalAccessException
  {
    if ((flags & Modifier.STATIC) != 0 || class_.isInstance(instance)) {
      if ((flags & Modifier.STATIC) != 0) {
        instance = null;
      }

      if (arguments == null) {
        if (parameterCount > 0) {
          throw new NullPointerException();
        }
        arguments = new Object[0];
      }

      if (arguments.length == parameterCount) {
        return invoke(this, instance, arguments);        
      } else {
        throw new ArrayIndexOutOfBoundsException();
      }
    } else {
      throw new IllegalArgumentException();
    }
  }

  private static native Object invoke(Method method, Object instance,
                                      Object ... arguments)
    throws InvocationTargetException, IllegalAccessException;

  public Class getReturnType() {
    for (int i = 0; i < spec.length - 1; ++i) {
      if (spec[i] == ')') {
        return Class.forCanonicalName
          (class_.getClassLoader(),
           new String(spec, i + 1, spec.length - i - 2, false));
      }
    }
    throw new RuntimeException();
  }

  public boolean isSynthetic() {
    throw new UnsupportedOperationException();
  }

  public Object getDefaultValue() {
    throw new UnsupportedOperationException();
  }

  public Type[] getGenericParameterTypes() {
    throw new UnsupportedOperationException();
  }

  public Type getGenericReturnType() {
    throw new UnsupportedOperationException();
  }

  public Class[] getExceptionTypes() {
    throw new UnsupportedOperationException();
  }

  public TypeVariable<Method<T>>[] getTypeParameters() {
    throw new UnsupportedOperationException();
  }
}
