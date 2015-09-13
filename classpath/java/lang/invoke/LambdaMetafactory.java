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
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import avian.Classes;
import avian.ConstantPool;
import avian.Assembler;
import avian.ConstantPool.PoolEntry;
import avian.SystemClassLoader;

public class LambdaMetafactory {
  private static int nextNumber = 0;
  
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
           + localType.footprint() + 2); // max stack
    write2(out, localType.footprint() + 1); // max locals
    write4(out, 0); // length (we'll set the real value later)

    write1(out, aload_0);

    for (MethodType.Parameter p: fieldType.parameters()) {
      write1(out, aload_0);
      write1(out, getfield);
      write2(out, ConstantPool.addFieldRef
             (pool, className, "field" + p.index(), p.spec()) + 1);
    }

    for (MethodType.Parameter p: localType.parameters()) {
      write1(out, p.load());
      write1(out, p.position() + 1);
    }

    switch (implementation.kind) {
    case MethodHandle.REF_invokeStatic:
      write1(out, invokestatic);
      break;

    case MethodHandle.REF_invokeSpecial:
      write1(out, invokespecial);
      break;

    default: throw new AssertionError
        ("todo: implement per http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.5");
    }
    
    write2(out, ConstantPool.addMethodRef
           (pool,
            Classes.makeString(implementation.method.class_.name, 0,
                               implementation.method.class_.name.length - 1),
            Classes.makeString(implementation.method.name, 0,
                               implementation.method.name.length - 1),
            Classes.makeString(implementation.method.spec, 0,
                               implementation.method.spec.length - 1)) + 1);

    write1(out, implementation.type().result().return_());

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    byte[] result = out.toByteArray();
    set4(result, 4, result.length - 12);

    return result;
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
                                       implementationKind));
  }

  private static byte[] makeLambda(String invokedName,
                                   MethodType invokedType,
                                   MethodType methodType,
                                   MethodHandle methodImplementation)
  {
    String className;
    { int number;
      synchronized (LambdaMetafactory.class) {
        number = nextNumber++;
      }
      className = "Lambda-" + number;
    }

    List<PoolEntry> pool = new ArrayList();

    int interfaceIndex = ConstantPool.addClass
      (pool, invokedType.returnType().getName().replace('.', '/'));

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
        (out, pool, nameIndex, superIndex, new int[] { interfaceIndex },
         fieldTable.toArray(new FieldData[fieldTable.size()]),
         methodTable.toArray(new MethodData[methodTable.size()]));
    } catch (IOException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;      
    }

    return out.toByteArray();
  }
  
  public static CallSite metafactory(MethodHandles.Lookup caller,
                                     String invokedName,
                                     MethodType invokedType,
                                     MethodType methodType,
                                     MethodHandle methodImplementation,
                                     MethodType instantiatedMethodType)
    throws LambdaConversionException
  {
    byte[] classData = makeLambda(invokedName, invokedType, methodType, methodImplementation);
    
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
}
