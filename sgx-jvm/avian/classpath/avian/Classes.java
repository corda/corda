/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import static avian.Stream.read1;
import static avian.Stream.read2;

import java.net.URL;
import java.net.MalformedURLException;
import java.security.CodeSource;
import java.security.AllPermission;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.annotation.Annotation;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class Classes {
  private static final int LinkFlag = 1 << 8;

  public static native VMClass defineVMClass
    (ClassLoader loader, byte[] b, int offset, int length);

  public static native VMClass primitiveClass(char name);

  public static native void initialize(VMClass vmClass);

  public static native boolean isAssignableFrom(VMClass a, VMClass b);

  public static native VMClass toVMClass(Class c);

  public static native VMMethod toVMMethod(Method m);

  private static native VMClass resolveVMClass(ClassLoader loader, byte[] spec)
    throws ClassNotFoundException;

  public static VMClass loadVMClass(ClassLoader loader,
                                    byte[] nameBytes, int offset, int length)
  {
    byte[] spec = new byte[length + 1];
    System.arraycopy(nameBytes, offset, spec, 0, length);

    try {
      VMClass c = resolveVMClass(loader, spec);
      if (c == null) {
        throw new NoClassDefFoundError();
      }
      return c;
    } catch (ClassNotFoundException e) {
      NoClassDefFoundError error = new NoClassDefFoundError
        (new String(nameBytes, offset, length));
      error.initCause(e);
      throw error;
    }
  }

  private static Object parseAnnotationValue(ClassLoader loader,
                                             Object pool,
                                             InputStream in)
    throws IOException
  {
    switch (read1(in)) {
    case 'Z':
      return Boolean.valueOf(Singleton.getInt(pool, read2(in) - 1) != 0);

    case 'B':
      return Byte.valueOf((byte) Singleton.getInt(pool, read2(in) - 1));

    case 'C':
      return Character.valueOf((char) Singleton.getInt(pool, read2(in) - 1));

    case 'S':
      return Short.valueOf((short) Singleton.getInt(pool, read2(in) - 1));

    case 'I':
      return Integer.valueOf(Singleton.getInt(pool, read2(in) - 1));

    case 'F':
      return Float.valueOf
        (Float.intBitsToFloat(Singleton.getInt(pool, read2(in) - 1)));

    case 'J': {
      return Long.valueOf(Singleton.getLong(pool, read2(in) - 1));
    }

    case 'D': {
      return Double.valueOf
        (Double.longBitsToDouble(Singleton.getLong(pool, read2(in) - 1)));
    }

    case 's': {
      byte[] data = (byte[]) Singleton.getObject(pool, read2(in) - 1);

      return new String(data, 0, data.length - 1);
    }

    case 'e': {
      byte[] typeName = (byte[]) Singleton.getObject(pool, read2(in) - 1);
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);

      return Enum.valueOf
        (SystemClassLoader.getClass
         (loadVMClass(loader, typeName, 1, typeName.length - 3)),
         new String(name, 0, name.length - 1));
    }

    case 'c':{
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);

      return SystemClassLoader.getClass
        (loadVMClass(loader, name, 1, name.length - 3));
    }

    case '@':
      return getAnnotation(loader, parseAnnotation(loader, pool, in));

    case '[': {
      Object[] array = new Object[read2(in)];
      for (int i = 0; i < array.length; ++i) {
        array[i] = parseAnnotationValue(loader, pool, in);
      }
      return array;
    }

    default: throw new AssertionError();
    }
  }

  private static Object[] parseAnnotation(ClassLoader loader,
                                          Object pool,
                                          InputStream in)
    throws IOException
  {
    byte[] typeName = (byte[]) Singleton.getObject(pool, read2(in) - 1);
    Object[] annotation = new Object[(read2(in) + 1) * 2];
    annotation[1] = SystemClassLoader.getClass
      (loadVMClass(loader, typeName, 1, typeName.length - 3));

    for (int i = 2; i < annotation.length; i += 2) {
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);
      annotation[i] = new String(name, 0, name.length - 1);
      annotation[i + 1] = parseAnnotationValue(loader, pool, in);
    }

    return annotation;
  }

  private static Object[] parseAnnotationTable(ClassLoader loader,
                                               Object pool,
                                               InputStream in)
    throws IOException
  {
    Object[] table = new Object[read2(in)];
    for (int i = 0; i < table.length; ++i) {
      table[i] = parseAnnotation(loader, pool, in);
    }
    return table;
  }

  private static void parseAnnotationTable(ClassLoader loader,
                                           Addendum addendum)
  {
    if (addendum != null && addendum.annotationTable instanceof byte[]) {
      try {
        addendum.annotationTable = parseAnnotationTable
          (loader, addendum.pool, new ByteArrayInputStream
           ((byte[]) addendum.annotationTable));
      } catch (IOException e) {
        AssertionError error = new AssertionError();
        error.initCause(e);
        throw error;
      }
    }
  }

  private static int resolveSpec(ClassLoader loader, byte[] spec, int start) {
    int result;
    int end;
    switch (spec[start]) {
    case 'L':
      ++ start;
      end = start;
      while (spec[end] != ';') ++ end;
      result = end + 1;
      break;

    case '[':
      end = start + 1;
      while (spec[end] == '[') ++ end;
      switch (spec[end]) {
      case 'L':
        ++ end;
        while (spec[end] != ';') ++ end;
        ++ end;
        break;

      default:
        ++ end;
      }
      result = end;
      break;

    default:
      return start + 1;
    }

    loadVMClass(loader, spec, start, end - start);

    return result;
  }

  private static int declaredMethodCount(VMClass c) {
    ClassAddendum a = c.addendum;
    if (a != null) {
      int count = a.declaredMethodCount;
      if (count >= 0) {
        return count;
      }
    }
    VMMethod[] table = c.methodTable;
    return table == null ? 0 : table.length;
  }

  public static void link(VMClass c, ClassLoader loader) {
    acquireClassLock();
    try {
      if ((c.vmFlags & LinkFlag) == 0) {
        if (c.super_ != null) {
          link(c.super_, loader);
        }

        parseAnnotationTable(loader, c.addendum);

        if (c.interfaceTable != null) {
          int stride = ((c.flags & Modifier.INTERFACE) != 0 ? 1 : 2);
          for (int i = 0; i < c.interfaceTable.length; i += stride) {
            link((VMClass) c.interfaceTable[i], loader);
          }
        }

        VMMethod[] methodTable = c.methodTable;
        if (methodTable != null) {
          for (int i = 0; i < methodTable.length; ++i) {
            VMMethod m = methodTable[i];

            for (int j = 1; j < m.spec.length;) {
              j = resolveSpec(loader, m.spec, j);
            }

            parseAnnotationTable(loader, m.addendum);
          }
        }

        if (c.fieldTable != null) {
          for (int i = 0; i < c.fieldTable.length; ++i) {
            VMField f = c.fieldTable[i];

            resolveSpec(loader, f.spec, 0);

            parseAnnotationTable(loader, f.addendum);
          }
        }

        c.vmFlags |= LinkFlag;
      }
    } finally {
      releaseClassLock();
    }
  }

  public static void link(VMClass c) {
    link(c, c.loader);
  }

  public static Class forName(String name, boolean initialize,
                              ClassLoader loader)
    throws ClassNotFoundException
  {
    if (loader == null) {
      loader = Class.class.getClassLoader();
    }
    Class c = loader.loadClass(name.replace('/', '.'));
    VMClass vmc = SystemClassLoader.vmClass(c);
    link(vmc, loader);
    if (initialize) {
      initialize(vmc);
    }
    return c;
  }

  public static Class forCanonicalName(String name) {
    return forCanonicalName(null, name);
  }

  public static Class forCanonicalName(ClassLoader loader, String name) {
    try {
      if (name.startsWith("[")) {
        return forName(name, true, loader);
      } else if (name.startsWith("L")) {
        return forName(name.substring(1, name.length() - 1), true, loader);
      } else {
        if (name.length() == 1) {
          return SystemClassLoader.getClass
            (primitiveClass(name.charAt(0)));
        } else {
          throw new ClassNotFoundException(name);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static int next(char c, String s, int start) {
    for (int i = start; i < s.length(); ++i) {
      if (s.charAt(i) == c) return i;
    }
    throw new RuntimeException();
  }

  public static Class[] getParameterTypes(VMMethod vmMethod) {
    int count = vmMethod.parameterCount;

    Class[] types = new Class[count];
    int index = 0;

    String spec = new String
      (vmMethod.spec, 1, vmMethod.spec.length - 2);

    try {
      for (int i = 0; i < spec.length(); ++i) {
        char c = spec.charAt(i);
        if (c == ')') {
          break;
        } else if (c == 'L') {
          int start = i + 1;
          i = next(';', spec, start);
          String name = spec.substring(start, i).replace('/', '.');
          types[index++] = Class.forName(name, true, vmMethod.class_.loader);
        } else if (c == '[') {
          int start = i;
          while (spec.charAt(i) == '[') ++i;

          if (spec.charAt(i) == 'L') {
            i = next(';', spec, i + 1);
            String name = spec.substring(start, i).replace('/', '.');
            types[index++] = Class.forName
              (name, true, vmMethod.class_.loader);
          } else {
            String name = spec.substring(start, i + 1);
            types[index++] = forCanonicalName(vmMethod.class_.loader, name);
          }
        } else {
          String name = spec.substring(i, i + 1);
          types[index++] = forCanonicalName(vmMethod.class_.loader, name);
        }
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    return types;
  }

  public static int findField(VMClass vmClass, String name) {
    if (vmClass.fieldTable != null) {
      link(vmClass);

      for (int i = 0; i < vmClass.fieldTable.length; ++i) {
        if (toString(vmClass.fieldTable[i].name).equals(name)) {
          return i;
        }
      }
    }
    return -1;
  }

  public static String toString(byte[] array) {
    return new String(array, 0, array.length - 1);
  }

  private static boolean match(VMClass a, VMClass b) {
    // TODO: in theory we should be able to just do an == comparison
    // here instead of recursively comparing array element types.
    // However, the VM currently can create multiple array classes for
    // the same element type.  We should fix that so that there's only
    // ever one of each per classloader, eliminating the need for a
    // recursive comparison.  See also the native implementation of
    // isAssignableFrom.
    if (a.arrayDimensions > 0) {
      return match(a.arrayElementClass, b.arrayElementClass);
    } else {
      return a == b;
    }
  }

  public static boolean match(Class[] a, Class[] b) {
    if (a.length == b.length) {
      for (int i = 0; i < a.length; ++i) {
        if (! match(toVMClass(a[i]), toVMClass(b[i]))) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  public static VMMethod findMethod(ClassLoader loader,
                                    String class_,
                                    String name,
                                    String spec)
    throws ClassNotFoundException
  {
    VMClass c = SystemClassLoader.vmClass(loader.loadClass(class_));
    VMMethod[] methodTable = c.methodTable;
    if (methodTable != null) {
      link(c);

      for (int i = 0; i < methodTable.length; ++i) {
        VMMethod m = methodTable[i];
        if (toString(m.name).equals(name) && toString(m.spec).equals(spec)) {
          return m;
        }
      }
    }
    return null;
  }

  public static int findMethod(VMClass vmClass, String name,
                                Class[] parameterTypes)
  {
    VMMethod[] methodTable = vmClass.methodTable;
    if (methodTable != null) {
      link(vmClass);

      if (parameterTypes == null) {
        parameterTypes = new Class[0];
      }

      for (int i = 0; i < methodTable.length; ++i) {
        VMMethod m = methodTable[i];
        if (toString(m.name).equals(name)
            && match(parameterTypes, getParameterTypes(m)))
        {
          return i;
        }
      }
    }
    return -1;
  }

  public static int countMethods(VMClass vmClass, boolean publicOnly) {
    int count = 0;
    VMMethod[] methodTable = vmClass.methodTable;
    if (methodTable != null) {
      for (int i = 0, j = declaredMethodCount(vmClass); i < j; ++i) {
        VMMethod m = methodTable[i];
        if (((! publicOnly) || ((m.flags & Modifier.PUBLIC)) != 0)
            && (! toString(m.name).startsWith("<")))
        {
          ++ count;
        }
      }
    }
    return count;
  }

  public static Method[] getMethods(VMClass vmClass, boolean publicOnly) {
    Method[] array = new Method[countMethods(vmClass, publicOnly)];
    VMMethod[] methodTable = vmClass.methodTable;
    if (methodTable != null) {
      link(vmClass);

      int ai = 0;
      for (int i = 0, j = declaredMethodCount(vmClass); i < j; ++i) {
        VMMethod m = methodTable[i];
        if (((! publicOnly) || ((m.flags & Modifier.PUBLIC) != 0))
            && ! toString(m.name).startsWith("<"))
        {
          array[ai++] = makeMethod(SystemClassLoader.getClass(vmClass), i);
        }
      }
    }

    return array;
  }

  public static int countFields(VMClass vmClass, boolean publicOnly) {
    int count = 0;
    if (vmClass.fieldTable != null) {
      for (int i = 0; i < vmClass.fieldTable.length; ++i) {
        if ((! publicOnly)
            || ((vmClass.fieldTable[i].flags & Modifier.PUBLIC))
            != 0)
        {
          ++ count;
        }
      }
    }
    return count;
  }

  public static Field[] getFields(VMClass vmClass, boolean publicOnly) {
    Field[] array = new Field[countFields(vmClass, publicOnly)];
    if (vmClass.fieldTable != null) {
      link(vmClass);

      int ai = 0;
      for (int i = 0; i < vmClass.fieldTable.length; ++i) {
        if (((vmClass.fieldTable[i].flags & Modifier.PUBLIC) != 0)
            || (! publicOnly))
        {
          array[ai++] = makeField(SystemClassLoader.getClass(vmClass), i);
        }
      }
    }

    return array;
  }

  public static Annotation getAnnotation(ClassLoader loader, Object[] a) {
    if (a[0] == null) {
      a[0] = Proxy.newProxyInstance
        (loader, new Class[] { (Class) a[1] },
         new AnnotationInvocationHandler(a));
    }
    return (Annotation) a[0];
  }

  public static Object getAnnotationDefaultValue(ClassLoader loader,
                                                 MethodAddendum addendum) {
    if (addendum == null) {
      return null;
    }
    byte[] annotationDefault = (byte[]) addendum.annotationDefault;
    if (annotationDefault == null) {
      return null;
    }
    try {
      return parseAnnotationValue(loader, addendum.pool,
        new ByteArrayInputStream(annotationDefault));
    } catch (IOException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }
  }

  private static int index(VMMethod m) {
    VMMethod[] table = m.class_.methodTable;
    for (int i = 0; i < table.length; ++i) {
      if (m == table[i]) return i;
    }
    throw new AssertionError();
  }

  public static Method makeMethod(VMMethod m) {
    return makeMethod(SystemClassLoader.getClass(m.class_), index(m));
  }

  public static ProtectionDomain getProtectionDomain(VMClass c) {
    CodeSource source = null;
    if (c.source != null) {
      try {
        source = new CodeSource
          (new URL(new String(c.source, 0, c.source.length - 1)),
           (Certificate[]) null);
      } catch (MalformedURLException ignored) { }
    }

    Permissions p = new Permissions();
    p.add(new AllPermission());

    return new ProtectionDomain(source, p);
  }

  public static native Method makeMethod(Class c, int slot);

  public static native Field makeField(Class c, int slot);

  private static native void acquireClassLock();

  private static native void releaseClassLock();

  public static native String makeString(byte[] array, int offset, int length);
}
