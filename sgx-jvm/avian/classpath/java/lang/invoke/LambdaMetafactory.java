/* Copyright (c) 2008-2016, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.invoke;

import static avian.Stream.write1;
import static avian.Stream.write2;
import static avian.Stream.write4;
import static avian.Stream.set4;
import static avian.Assembler.*;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;

import avian.Classes;
import avian.ConstantPool;
import avian.Assembler;
import avian.ConstantPool.PoolEntry;
import avian.SystemClassLoader;

// To understand what this is all about, please read:
//
//   http://cr.openjdk.java.net/~briangoetz/lambda/lambda-translation.html

public class LambdaMetafactory {
  private static int nextNumber = 0;

  public static final int FLAG_SERIALIZABLE = 1;
  public static final int FLAG_MARKERS = 2;
  public static final int FLAG_BRIDGES = 4;

  private static Class resolveReturnInterface(MethodType type) {
    int index = 1;
    byte[] s = type.spec;

    while (s[index] != ')') ++ index;

    if (s[++ index] != 'L') throw new AssertionError();

    ++ index;

    int end = index + 1;
    while (s[end] != ';') ++ end;

    Class c = SystemClassLoader.getClass
      (Classes.loadVMClass(type.loader, s, index, end - index));

    if (! c.isInterface()) throw new AssertionError();

    return c;
  }

  private static int indexOf(int c, byte[] array) {
    int i = 0;
    while (array[i] != c) ++i;
    return i;
  }

  private static String constructorSpec(MethodType type) {
    return Classes.makeString(type.spec, 0, indexOf(')', type.spec) + 1) + "V";
  }

  private static byte[] makeFactoryCode(List<PoolEntry> pool,
                                        String className,
                                        String constructorSpec,
                                        MethodType type)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write2(out, type.footprint() + 2); // max stack
    write2(out, type.footprint()); // max locals
    write4(out, 0); // length (we'll set the real value later)

    write1(out, new_);
    write2(out, ConstantPool.addClass(pool, className) + 1);
    write1(out, dup);

    for (MethodType.Parameter p: type.parameters()) {
      write1(out, p.load());
      write1(out, p.position());
    }

    write1(out, invokespecial);
    write2(out, ConstantPool.addMethodRef
           (pool, className, "<init>", constructorSpec) + 1);

    write1(out, areturn);

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    byte[] result = out.toByteArray();
    set4(result, 4, result.length - 12);

    return result;
  }

  private static byte[] makeConstructorCode(List<PoolEntry> pool,
                                            String className,
                                            MethodType type)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write2(out, 3); // max stack
    write2(out, type.footprint() + 1); // max locals
    write4(out, 0); // length (we'll set the real value later)

    write1(out, aload_0);
    write1(out, invokespecial);
    write2(out, ConstantPool.addMethodRef
           (pool, "java/lang/Object", "<init>", "()V") + 1);

    for (MethodType.Parameter p: type.parameters()) {
      write1(out, aload_0);
      write1(out, p.load());
      write1(out, p.position() + 1);
      write1(out, putfield);
      write2(out, ConstantPool.addFieldRef
             (pool, className, "field" + p.index(), p.spec()) + 1);
    }

    write1(out, return_);

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    byte[] result = out.toByteArray();
    set4(result, 4, result.length - 12);

    return result;
  }

  private static void maybeBoxOrUnbox(ByteArrayOutputStream out,
                                      List<PoolEntry> pool,
                                      MethodType.TypeSpec from,
                                      MethodType.TypeSpec to)
    throws IOException
  {
    if (to.type().isPrimitive()) {
      if (! from.type().isPrimitive()) {
        write1(out, invokevirtual);

        try {
          switch (to.spec()) {
          case "Z":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Boolean.class.getMethod("booleanValue")));
            break;

          case "B":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Byte.class.getMethod("byteValue")));
            break;

          case "S":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Short.class.getMethod("shortValue")));
            break;

          case "C":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Character.class.getMethod("charValue")));
            break;

          case "I":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Integer.class.getMethod("intValue")));
            break;

          case "F":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Float.class.getMethod("floatValue")));
            break;

          case "J":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Long.class.getMethod("longValue")));
            break;

          case "D":
            writeMethodReference(out, pool, Classes.toVMMethod
                                 (Double.class.getMethod("doubleValue")));
            break;

          default:
            throw new AssertionError("don't know how to auto-unbox to " + to.spec());
          }
        } catch (NoSuchMethodException e) {
          throw new Error(e);
        }
      }
    } else if (from.type().isPrimitive()) {
      write1(out, invokestatic);

      try {
        switch (from.spec()) {
        case "Z":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Boolean.class.getMethod
                                ("valueOf", Boolean.TYPE)));
          break;

        case "B":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Byte.class.getMethod
                                ("valueOf", Byte.TYPE)));
          break;

        case "S":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Short.class.getMethod
                                ("valueOf", Short.TYPE)));
          break;

        case "C":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Character.class.getMethod
                                ("valueOf", Character.TYPE)));
          break;

        case "I":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Integer.class.getMethod
                                ("valueOf", Integer.TYPE)));
          break;

        case "F":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Float.class.getMethod
                                ("valueOf", Float.TYPE)));
          break;

        case "J":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Long.class.getMethod
                                ("valueOf", Long.TYPE)));
          break;

        case "D":
          writeMethodReference(out, pool, Classes.toVMMethod
                               (Double.class.getMethod
                                ("valueOf", Double.TYPE)));
          break;

        default:
          throw new AssertionError("don't know how to autobox from " + from.spec());
        }
      } catch (NoSuchMethodException e) {
        throw new Error(e);
      }
    }
  }

  private static byte[] makeInvocationCode(List<PoolEntry> pool,
                                           String className,
                                           String constructorSpec,
                                           MethodType fieldType,
                                           MethodType localType,
                                           MethodHandle implementation)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write2(out, fieldType.footprint()
           + localType.footprint() + 4); // max stack
    write2(out, localType.footprint() + 1); // max locals
    write4(out, 0); // length (we'll set the real value later)

    write1(out, aload_0);

    Iterator<MethodType.Parameter> dst = implementation.type().parameters().iterator();

    boolean skip = implementation.kind != MethodHandle.REF_invokeStatic;

    for (MethodType.Parameter p: fieldType.parameters()) {
      write1(out, aload_0);
      write1(out, getfield);
      write2(out, ConstantPool.addFieldRef
             (pool, className, "field" + p.index(), p.spec()) + 1);
      if (skip) {
        skip = false;
      } else {
        maybeBoxOrUnbox(out, pool, p, dst.next());
      }
    }

    for (MethodType.Parameter p: localType.parameters()) {
      write1(out, p.load());
      write1(out, p.position() + 1);
      if (skip) {
        skip = false;
      } else {
        maybeBoxOrUnbox(out, pool, p, dst.next());
      }
    }

    switch (implementation.kind) {
    case MethodHandle.REF_invokeVirtual:
      write1(out, invokevirtual);
      writeMethodReference(out, pool, implementation.method);
      break;

    case MethodHandle.REF_invokeStatic:
      write1(out, invokestatic);
      writeMethodReference(out, pool, implementation.method);
      break;

    case MethodHandle.REF_invokeSpecial:
      write1(out, invokespecial);
      writeMethodReference(out, pool, implementation.method);
      break;

    case MethodHandle.REF_newInvokeSpecial:
      write1(out, new_);
      write2(out, ConstantPool.addClass
             (pool,
              Classes.makeString
              (implementation.method.class_.name, 0,
               implementation.method.class_.name.length - 1)) + 1);
      write1(out, dup);
      write1(out, invokespecial);
      writeMethodReference(out, pool, implementation.method);
      break;

    case MethodHandle.REF_invokeInterface:
      write1(out, invokeinterface);
      writeInterfaceMethodReference(out, pool, implementation.method);
      write1(out, implementation.method.parameterFootprint);
      write1(out, 0);
      break;

    default: throw new AssertionError
        ("todo: implement '" + implementation.kind + "' per http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.5");
    }

    if (implementation.kind != MethodHandle.REF_newInvokeSpecial) {
      maybeBoxOrUnbox(out, pool, implementation.type().result(), localType.result());
    }
    write1(out, localType.result().return_());

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    byte[] result = out.toByteArray();
    set4(result, 4, result.length - 12);

    return result;
  }

  private static void writeMethodReference(OutputStream out,
                                           List<PoolEntry> pool,
                                           avian.VMMethod method)
    throws IOException
  {
    write2(out, ConstantPool.addMethodRef
           (pool,
            Classes.makeString(method.class_.name, 0,
                               method.class_.name.length - 1),
            Classes.makeString(method.name, 0,
                               method.name.length - 1),
            Classes.makeString(method.spec, 0,
                               method.spec.length - 1)) + 1);
  }

  private static void writeInterfaceMethodReference(OutputStream out,
                                                    List<PoolEntry> pool,
                                                    avian.VMMethod method)
    throws IOException
  {
    write2(out, ConstantPool.addInterfaceMethodRef
           (pool,
            Classes.makeString(method.class_.name, 0,
                               method.class_.name.length - 1),
            Classes.makeString(method.name, 0,
                               method.name.length - 1),
            Classes.makeString(method.spec, 0,
                               method.spec.length - 1)) + 1);
  }

  public static byte[] makeLambda(String invokedName,
                                  String invokedType,
                                  String methodType,
                                  String implementationClass,
                                  String implementationName,
                                  String implementationSpec,
                                  int implementationKind)
  {
    return makeLambda(invokedName,
                      new MethodType(invokedType),
                      new MethodType(methodType),
                      new MethodHandle(implementationClass,
                                       implementationName,
                                       implementationSpec,
                                       implementationKind),
                      emptyInterfaceList);
  }

  private static byte[] makeLambda(String invokedName,
                                   MethodType invokedType,
                                   MethodType methodType,
                                   MethodHandle methodImplementation,
                                   Class[] interfaces)
  {
    String className;
    { int number;
      synchronized (LambdaMetafactory.class) {
        number = nextNumber++;
      }
      className = "Lambda-" + number;
    }

    List<PoolEntry> pool = new ArrayList();

    int[] interfaceIndexes = new int[interfaces.length + 1];
    interfaceIndexes[0] = ConstantPool.addClass(pool, invokedType.returnType().getName().replace('.', '/'));
    for (int i = 0; i < interfaces.length; i++) {
      String name = interfaces[i].getName().replace('.', '/');
      interfaceIndexes[i + 1] = ConstantPool.addClass(pool, name);
    }

    List<FieldData> fieldTable = new ArrayList();

    for (MethodType.Parameter p: invokedType.parameters()) {
      fieldTable.add
        (new FieldData(0,
                       ConstantPool.addUtf8(pool, "field" + p.index()),
                       ConstantPool.addUtf8(pool, p.spec())));
    }

    String constructorSpec = constructorSpec(invokedType);

    List<MethodData> methodTable = new ArrayList();

    try {
      methodTable.add
        (new MethodData
         (Modifier.PUBLIC | Modifier.STATIC,
          ConstantPool.addUtf8(pool, "make"),
          ConstantPool.addUtf8(pool, invokedType.toMethodDescriptorString()),
          makeFactoryCode(pool, className, constructorSpec, invokedType)));

      methodTable.add
        (new MethodData
         (Modifier.PUBLIC,
          ConstantPool.addUtf8(pool, "<init>"),
          ConstantPool.addUtf8(pool, constructorSpec),
          makeConstructorCode(pool, className, invokedType)));

      methodTable.add
        (new MethodData
         (Modifier.PUBLIC,
          ConstantPool.addUtf8(pool, invokedName),
          ConstantPool.addUtf8(pool, methodType.toMethodDescriptorString()),
          makeInvocationCode(pool, className, constructorSpec, invokedType,
                             methodType, methodImplementation)));
    } catch (IOException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }

    int nameIndex = ConstantPool.addClass(pool, className);
    int superIndex = ConstantPool.addClass(pool, "java/lang/Object");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      Assembler.writeClass
        (out, pool, nameIndex, superIndex, interfaceIndexes,
         fieldTable.toArray(new FieldData[fieldTable.size()]),
         methodTable.toArray(new MethodData[methodTable.size()]));
    } catch (IOException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }

    return out.toByteArray();
  }

  private static CallSite makeCallSite(MethodType invokedType, byte[] classData) throws AssertionError {
    try {
      return new CallSite
              (new MethodHandle
                      (MethodHandle.REF_invokeStatic, invokedType.loader, Classes.toVMMethod
                              (avian.SystemClassLoader.getClass
                                      (avian.Classes.defineVMClass
                                              (invokedType.loader, classData, 0, classData.length))
                                      .getMethod("make", invokedType.parameterArray()))));
    } catch (NoSuchMethodException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }
  }

  private static final Class[] emptyInterfaceList = new Class[] {};

  public static CallSite metafactory(MethodHandles.Lookup caller,
                                     String invokedName,
                                     MethodType invokedType,
                                     MethodType methodType,
                                     MethodHandle methodImplementation,
                                     MethodType instantiatedMethodType)
    throws LambdaConversionException
  {
    byte[] classData = makeLambda(invokedName, invokedType, methodType, methodImplementation, emptyInterfaceList);
    return makeCallSite(invokedType, classData);
  }

  public static CallSite altMetafactory(MethodHandles.Lookup caller,
                                        String invokedName,
                                        MethodType invokedType,
                                        Object... args) throws LambdaConversionException {
    // See openjdk8/jdk/src/share/classes/java/lang/invoke/LambdaMetafactory.java
    // Behaves as if the prototype is like this:
    //
    // CallSite altMetafactory(
    //    MethodHandles.Lookup caller,
    //    String invokedName,
    //    MethodType invokedType,
    //    MethodType methodType,
    //    MethodHandle methodImplementation,
    //    MethodType instantiatedMethodType,
    //    int flags,
    //    int markerInterfaceCount,  // IF flags has MARKERS set
    //    Class... markerInterfaces, // IF flags has MARKERS set
    //    int bridgeCount,           // IF flags has BRIDGES set
    //    MethodType... bridges      // IF flags has BRIDGES set
    //  )
    MethodType methodType = (MethodType) args[0];
    MethodHandle methodImplementation = (MethodHandle) args[1];

    int flags = (Integer) args[3];
    boolean serializable = (flags & FLAG_SERIALIZABLE) != 0;

    // Marker interfaces are added to a lambda when they're written like this:
    //
    //    Runnable r = (Runnable & Serializable) () -> foo()
    //
    // The intersection type in the cast here indicates to the compiler what interfaces
    // the generated lambda class should implement. Because a lambda has (by definition)
    // one method only, it is meaningless for these interfaces to contain anything, thus
    // they are only allowed to be empty marker interfaces. In practice the Serializable
    // interface is handled specially and the use of markers is extremely rare. Adding
    // support would be easy though.
    if ((flags & FLAG_MARKERS) != 0)
      throw new UnsupportedOperationException("Marker interfaces on lambdas are not supported on Avian yet. Sorry.");

    // In some cases there is a mismatch between what the JVM type system supports and
    // what the Java language supports. In other cases the type of a lambda expression
    // may not perfectly match the functional interface which represents it. Consider the
    // following case:
    //
    //    interface I { void foo(Integer i, String s1, Strings s2) }
    //    class Foo { static void m(Number i, Object... rest) {} }
    //
    //    I lambda = Foo::m
    //
    // This is allowed by the Java language, even though the interface representing the
    // lambda specifies three specific arguments and the method implementing the lambda
    // uses varargs and a different type signature. Behind the scenes the compiler generates
    // a "bridge" method that does the adaptation.
    //
    // You can learn more here: http://www.oracle.com/technetwork/java/jvmls2013heid-2013922.pdf
    // and here: http://cr.openjdk.java.net/~briangoetz/lambda/lambda-translation.html
    if ((flags & FLAG_BRIDGES) != 0) {
      int bridgeCount = (Integer) args[4];
      if (bridgeCount > 0)
        throw new UnsupportedOperationException("A lambda that requires bridge methods was used, this is not yet supported by Avian. Sorry.");
    }

    // TODO: This is not necessary if the function type interface is already inheriting
    // from Serializable.
    Class[] interfaces = new Class[serializable ? 1 : 0];
    if (serializable)
      interfaces[0] = java.io.Serializable.class;

    byte[] classData = makeLambda(invokedName, invokedType, methodType, methodImplementation, interfaces);
    return makeCallSite(invokedType, classData);
  }
}
