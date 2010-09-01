/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package avian;

import static avian.Stream.read1;
import static avian.Stream.read2;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SystemClassLoader extends ClassLoader {
  private static final int LinkFlag = 1 << 8;

  public static native VMClass defineVMClass
    (ClassLoader loader, byte[] b, int offset, int length);

  private static native VMClass findVMClass(String name)
    throws ClassNotFoundException;

  protected Class findClass(String name) throws ClassNotFoundException {
    return getClass(findVMClass(name));
  }

  private static native VMClass findLoadedVMClass(String name);

  protected Class reallyFindLoadedClass(String name){
    VMClass c = findLoadedVMClass(name);
    return c == null ? null : getClass(c);
  }

  private static native boolean resourceExists(String name);

  private static native VMClass resolveVMClass(ClassLoader loader, byte[] spec)
    throws ClassNotFoundException;

  protected URL findResource(String name) {
    if (resourceExists(name)) {
      try {
        return new URL("resource:" + name);
      } catch (MalformedURLException ignored) { }
    }
    return null;
  }

  private static VMClass loadVMClass(ClassLoader loader,
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
        (new String(nameBytes, offset, length, false));
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

      return new String(data, 0, data.length - 1, false);
    }

    case 'e': {
      byte[] typeName = (byte[]) Singleton.getObject(pool, read2(in) - 1);
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);

      return Enum.valueOf
        (getClass(loadVMClass(loader, typeName, 1, typeName.length - 3)),
         new String(name, 0, name.length - 1, false));
    }

    case 'c':{
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);

      return getClass(loadVMClass(loader, name, 1, name.length - 3));
    }

    case '@':
      return parseAnnotation(loader, pool, in);

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
    annotation[1] = getClass
      (loadVMClass(loader, typeName, 1, typeName.length - 3));

    for (int i = 2; i < annotation.length; i += 2) {
      byte[] name = (byte[]) Singleton.getObject(pool, read2(in) - 1);
      annotation[i] = new String(name, 0, name.length - 1, false);
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

      addendum.pool = null;
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

  private static native void acquireClassLock();

  private static native void releaseClassLock();

  public static Class getClass(VMClass vmClass) {
    if (vmClass.addendum == null) {
      SystemClassLoader.acquireClassLock();
      try {
        if (vmClass.addendum == null) {
          vmClass.addendum = new ClassAddendum();
        }
      } finally {
        SystemClassLoader.releaseClassLock();
      }
    }

    if (vmClass.addendum.class_ == null) {
      SystemClassLoader.acquireClassLock();
      try {
        if (vmClass.addendum.class_ == null) {
          vmClass.addendum.class_ = new Class(vmClass);
        }
      } finally {
        SystemClassLoader.releaseClassLock();
      }
    }

    return vmClass.addendum.class_;
  }

  public static native VMClass getVMClass(Object o);

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

        if (c.methodTable != null) {
          for (int i = 0; i < c.methodTable.length; ++i) {
            VMMethod m = c.methodTable[i];

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
}
