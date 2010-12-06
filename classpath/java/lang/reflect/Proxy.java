/* Copyright (c) 2009-2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

import static avian.Stream.write1;
import static avian.Stream.write2;
import static avian.Stream.write4;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Proxy {
  private static final int CONSTANT_Integer = 3;
  private static final int CONSTANT_Utf8 = 1;
  private static final int CONSTANT_Class = 7;
  private static final int CONSTANT_NameAndType = 12;
  private static final int CONSTANT_Fieldref = 9;
  private static final int CONSTANT_Methodref = 10;

  private static final int aaload = 0x32;
  private static final int aastore = 0x53;
  private static final int aload = 0x19;
  private static final int aload_0 = 0x2a;
  private static final int aload_1 = 0x2b;
  private static final int anewarray = 0xbd;
  private static final int areturn = 0xb0;
  private static final int dload = 0x18;
  private static final int dreturn = 0xaf;
  private static final int dup = 0x59;
  private static final int fload = 0x17;
  private static final int freturn = 0xae;
  private static final int getfield = 0xb4;
  private static final int iload = 0x15;
  private static final int invokeinterface = 0xb9;
  private static final int invokespecial = 0xb7;
  private static final int invokestatic = 0xb8;
  private static final int invokevirtual = 0xb6;
  private static final int ireturn = 0xac;
  private static final int ldc_w = 0x13;
  private static final int lload = 0x16;
  private static final int lreturn = 0xad;
  private static final int new_ = 0xbb;
  private static final int pop = 0x57;
  private static final int putfield = 0xb5;
  private static final int return_ = 0xb1;

  private static int nextNumber;

  protected InvocationHandler h;

  public static Class getProxyClass(ClassLoader loader,
                                    Class ... interfaces)
  {
    for (Class c: interfaces) {
      if (! c.isInterface()) {
        throw new IllegalArgumentException();
      }
    }

    int number;
    synchronized (Proxy.class) {
      number = nextNumber++;
    }

    try {
      return makeClass(loader, interfaces, "Proxy-" + number);
    } catch (IOException e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;      
    }
  }

  public static boolean isProxyClass(Class c) {
    return c.getName().startsWith("Proxy-");
  }

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return ((Proxy) proxy).h;
  }

  private static void set4(byte[] array, int offset, int v) {
    array[offset    ] = (byte) ((v >>> 24) & 0xFF);
    array[offset + 1] = (byte) ((v >>> 16) & 0xFF);
    array[offset + 2] = (byte) ((v >>>  8) & 0xFF);
    array[offset + 3] = (byte) ((v       ) & 0xFF);
  }

  private static int poolAdd(List<PoolEntry> pool, PoolEntry e) {
    int i = 0;
    for (PoolEntry existing: pool) {
      if (existing.equals(e)) {
        return i;
      } else {
        ++i;
      }
    }
    pool.add(e);
    return pool.size() - 1;
  }

  private static int poolAddInteger(List<PoolEntry> pool, int value) {
    return poolAdd(pool, new IntegerPoolEntry(value));
  }

  private static int poolAddUtf8(List<PoolEntry> pool, String value) {
    return poolAdd(pool, new Utf8PoolEntry(value));
  }

  private static int poolAddClass(List<PoolEntry> pool, String name) {
    return poolAdd(pool, new ClassPoolEntry(poolAddUtf8(pool, name)));
  }

  private static int poolAddNameAndType(List<PoolEntry> pool,
                                        String name,
                                        String type)
  {
    return poolAdd(pool, new NameAndTypePoolEntry
                   (poolAddUtf8(pool, name),
                    poolAddUtf8(pool, type)));
  }

  private static int poolAddFieldRef(List<PoolEntry> pool,
                                     String className,
                                     String name,
                                     String spec)
  {
    return poolAdd(pool, new FieldRefPoolEntry
                   (poolAddClass(pool, className),
                    poolAddNameAndType(pool, name, spec)));
  }

  private static int poolAddMethodRef(List<PoolEntry> pool,
                                      String className,
                                      String name,
                                      String spec)
  {
    return poolAdd(pool, new MethodRefPoolEntry
                   (poolAddClass(pool, className),
                    poolAddNameAndType(pool, name, spec)));
  }

  private static byte[] makeInvokeCode(List<PoolEntry> pool,
                                       String className,
                                       byte[] spec,
                                       int parameterCount,
                                       int parameterFootprint,
                                       int index)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write2(out, 8); // max stack
    write2(out, parameterFootprint); // max locals
    write4(out, 0); // length (we'll set the real value later)

    write1(out, aload_0);
    write1(out, getfield);
    write2(out, poolAddFieldRef
           (pool, "java/lang/reflect/Proxy",
            "h", "Ljava/lang/reflect/InvocationHandler;") + 1);

    write1(out, aload_0);
    
    write1(out, new_);
    write2(out, poolAddClass(pool, "java/lang/reflect/Method") + 1);
    write1(out, dup);
    write1(out, ldc_w);
    write2(out, poolAddClass(pool, className) + 1);
    write1(out, getfield);
    write2(out, poolAddFieldRef
           (pool, "java/lang/Class",
            "vmClass", "Lavian/VMClass;") + 1);
    write1(out, getfield);
    write2(out, poolAddFieldRef
           (pool, "avian/VMClass",
            "methodTable", "[Lavian/VMMethod;") + 1);
    write1(out, ldc_w);
    write2(out, poolAddInteger(pool, index) + 1);
    write1(out, aaload);
    write1(out, invokespecial);
    write2(out, poolAddMethodRef
           (pool, "java/lang/reflect/Method",
            "<init>", "(Lavian/VMMethod;)V") + 1);

    write1(out, ldc_w);
    write2(out, poolAddInteger(pool, parameterCount) + 1);
    write1(out, anewarray);
    write2(out, poolAddClass(pool, "java/lang/Object") + 1);

    int ai = 0;
    int si;
    for (si = 1; spec[si] != ')'; ++si) {
      write1(out, dup);

      write1(out, ldc_w);
      write2(out, poolAddInteger(pool, ai) + 1);
    
      switch (spec[si]) {
      case 'L':
        ++ si;
        while (spec[si] != ';') ++si;
      
        write1(out, aload);
        write1(out, ai + 1);
        break;

      case '[':
        ++ si;
        while (spec[si] == '[') ++si;
        switch (spec[si]) {
        case 'L':
          ++ si;
          while (spec[si] != ';') ++si;
          break;

        default:
          break;
        }

        write1(out, aload);
        write1(out, ai + 1);
        break;

      case 'Z':
        write1(out, iload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Boolean",
                "valueOf", "(Z)Ljava/lang/Boolean;") + 1);
        break;

      case 'B':
        write1(out, iload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Byte",
                "valueOf", "(B)Ljava/lang/Byte;") + 1);
        break;

      case 'S':
        write1(out, iload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Short",
                "valueOf", "(S)Ljava/lang/Short;") + 1);
        break;

      case 'C':
        write1(out, iload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Character",
                "valueOf", "(C)Ljava/lang/Character;") + 1);
        break;

      case 'I':
        write1(out, iload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Integer",
                "valueOf", "(I)Ljava/lang/Integer;") + 1);
        break;

      case 'F':
        write1(out, fload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Float",
                "valueOf", "(F)Ljava/lang/Float;") + 1);
        break;

      case 'J':
        write1(out, lload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Long",
                "valueOf", "(J)Ljava/lang/Long;") + 1);
        ++ ai;
        break;

      case 'D':
        write1(out, dload);
        write1(out, ai + 1);

        write1(out, invokestatic);
        write2(out, poolAddMethodRef
               (pool, "java/lang/Double",
                "valueOf", "(D)Ljava/lang/Double;") + 1);
        ++ ai;
        break;

      default: throw new IllegalArgumentException();
      }

      write1(out, aastore);

      ++ ai;
    }

    write1(out, invokeinterface);
    write2(out, poolAddMethodRef
           (pool, "java/lang/reflect/InvocationHandler",
            "invoke",
            "(Ljava/lang/Object;"
            + "Ljava/lang/reflect/Method;"
            + "[Ljava/lang/Object;)"
            + "Ljava/lang/Object;") + 1);
    write2(out, 0); // this will be ignored by the VM

    switch (spec[si + 1]) {
    case 'L':
    case '[':
      write1(out, areturn);
      break;

    case 'Z':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Boolean", "booleanValue", "()Z") + 1);
      write1(out, ireturn);
      break;

    case 'B':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Byte", "byteValue", "()B") + 1);
      write1(out, ireturn);
      break;

    case 'C':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Character", "charValue", "()C") + 1);
      write1(out, ireturn);
      break;

    case 'S':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Short", "shortValue", "()S") + 1);
      write1(out, ireturn);
      break;

    case 'I':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Integer", "intValue", "()I") + 1);
      write1(out, ireturn);
      break;

    case 'F':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Float", "floatValue", "()F") + 1);
      write1(out, freturn);
      break;

    case 'J':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Long", "longValue", "()J") + 1);
      write1(out, lreturn);
      break;

    case 'D':
      write1(out, invokevirtual);
      write2(out, poolAddMethodRef
             (pool, "java/lang/Double", "doubleValue", "()D") + 1);
      write1(out, dreturn);
      break;

    case 'V':
      write1(out, pop);
      write1(out, return_);
      break;

    default: throw new IllegalArgumentException();
    }

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    byte[] result = out.toByteArray();
    set4(result, 4, result.length - 12);

    return result;
  }

  private static byte[] makeConstructorCode(List<PoolEntry> pool)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write2(out, 2); // max stack
    write2(out, 2); // max locals
    write4(out, 6); // length

    write1(out, aload_0);
    write1(out, aload_1);
    write1(out, putfield);
    write2(out, poolAddFieldRef
           (pool, "java/lang/reflect/Proxy",
            "h", "Ljava/lang/reflect/InvocationHandler;") + 1);
    write1(out, return_);

    write2(out, 0); // exception handler table length
    write2(out, 0); // attribute count

    return out.toByteArray();
  }

  private static Class makeClass(ClassLoader loader,
                                 Class[] interfaces,
                                 String name)
    throws IOException
  {
    List<PoolEntry> pool = new ArrayList();

    int[] interfaceIndexes = new int[interfaces.length];
    for (int i = 0; i < interfaces.length; ++i) {
      interfaceIndexes[i] = poolAddClass(pool, interfaces[i].getName());
    }

    Map<String,avian.VMMethod> virtualMap = new HashMap();
    for (Class c: interfaces) {
      avian.VMMethod[] ivtable = c.vmClass.virtualTable;
      if (ivtable != null) {
        for (avian.VMMethod m: ivtable) {
          virtualMap.put(Method.getName(m) + Method.getSpec(m), m);
        }
      }
    }

    MethodData[] methodTable = new MethodData[virtualMap.size() + 1];
    { int i = 0;
      for (avian.VMMethod m: virtualMap.values()) {
        methodTable[i] = new MethodData
          (poolAddUtf8(pool, Method.getName(m)),
           poolAddUtf8(pool, Method.getSpec(m)),
           makeInvokeCode(pool, name, m.spec, m.parameterCount,
                          m.parameterFootprint, i));
        ++ i;
      }
      
      methodTable[i++] = new MethodData
        (poolAddUtf8(pool, "<init>"),
         poolAddUtf8(pool, "(Ljava/lang/reflect/InvocationHandler;)V"),
         makeConstructorCode(pool));
    }

    int nameIndex = poolAddClass(pool, name);
    int superIndex = poolAddClass(pool, "java/lang/reflect/Proxy");
    int codeAttributeNameIndex = poolAddUtf8(pool, "Code");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write4(out, 0xCAFEBABE);
    write2(out, 0); // minor version
    write2(out, 0); // major version

    write2(out, pool.size() + 1);
    for (PoolEntry e: pool) {
      e.writeTo(out);
    }

    write2(out, 0); // flags
    write2(out, nameIndex + 1);
    write2(out, superIndex + 1);
    
    write2(out, interfaces.length);
    for (int i: interfaceIndexes) {
      write2(out, i + 1);
    }

    write2(out, 0); // field count

    write2(out, methodTable.length);
    for (MethodData m: methodTable) {
      write2(out, 0); // flags
      write2(out, m.nameIndex + 1);
      write2(out, m.specIndex + 1);

      write2(out, 1); // attribute count
      write2(out, codeAttributeNameIndex + 1);
      write4(out, m.code.length);
      out.write(m.code);
    }

    write2(out, 0); // attribute count

    byte[] classData = out.toByteArray();
    return avian.SystemClassLoader.getClass
      (avian.Classes.defineVMClass(loader, classData, 0, classData.length));
  }

  public static Object newProxyInstance(ClassLoader loader,
                                        Class[] interfaces,
                                        InvocationHandler handler)
  {
    try {
      return Proxy.getProxyClass(loader, interfaces)
        .getConstructor(new Class[] { InvocationHandler.class })
        .newInstance(new Object[] { handler });
    } catch (Exception e) {
      AssertionError error = new AssertionError();
      error.initCause(e);
      throw error;
    }
  }

  private static class MethodData {
    public final int nameIndex;
    public final int specIndex;
    public final byte[] code;

    public MethodData(int nameIndex, int specIndex, byte[] code) {
      this.nameIndex = nameIndex;
      this.specIndex = specIndex;
      this.code = code;
    }
  }

  public interface PoolEntry {
    public void writeTo(OutputStream out) throws IOException;
  }

  public static class IntegerPoolEntry implements PoolEntry {
    private final int value;

    public IntegerPoolEntry(int value) {
      this.value = value;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_Integer);
      write4(out, value);
    }

    public boolean equals(Object o) {
      return o instanceof IntegerPoolEntry 
        && ((IntegerPoolEntry) o).value == value;
    }
  }

  public static class Utf8PoolEntry implements PoolEntry {
    private final String data;

    public Utf8PoolEntry(String data) {
      this.data = data;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_Utf8);
      byte[] bytes = data.getBytes();
      write2(out, bytes.length);
      out.write(bytes);
    }

    public boolean equals(Object o) {
      return o instanceof Utf8PoolEntry
        && ((Utf8PoolEntry) o).data.equals(data);
    }
  }

  public static class ClassPoolEntry implements PoolEntry {
    private final int nameIndex;

    public ClassPoolEntry(int nameIndex) {
      this.nameIndex = nameIndex;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_Class);
      write2(out, nameIndex + 1);
    }

    public boolean equals(Object o) {
      return o instanceof ClassPoolEntry 
        && ((ClassPoolEntry) o).nameIndex == nameIndex;
    }
  }

  public static class NameAndTypePoolEntry implements PoolEntry {
    private final int nameIndex;
    private final int typeIndex;

    public NameAndTypePoolEntry(int nameIndex, int typeIndex) {
      this.nameIndex = nameIndex;
      this.typeIndex = typeIndex;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_NameAndType);
      write2(out, nameIndex + 1);
      write2(out, typeIndex + 1);
    }

    public boolean equals(Object o) {
      if (o instanceof NameAndTypePoolEntry) {
        NameAndTypePoolEntry other = (NameAndTypePoolEntry) o;
        return other.nameIndex == nameIndex && other.typeIndex == typeIndex;
      } else {
        return false;
      }
    }
  }

  public static class FieldRefPoolEntry implements PoolEntry {
    private final int classIndex;
    private final int nameAndTypeIndex;

    public FieldRefPoolEntry(int classIndex, int nameAndTypeIndex) {
      this.classIndex = classIndex;
      this.nameAndTypeIndex = nameAndTypeIndex;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_Fieldref);
      write2(out, classIndex + 1);
      write2(out, nameAndTypeIndex + 1);
    }

    public boolean equals(Object o) {
      if (o instanceof FieldRefPoolEntry) {
        FieldRefPoolEntry other = (FieldRefPoolEntry) o;
        return other.classIndex == classIndex
          && other.nameAndTypeIndex == nameAndTypeIndex;
      } else {
        return false;
      }
    }
  }

  public static class MethodRefPoolEntry implements PoolEntry {
    private final int classIndex;
    private final int nameAndTypeIndex;

    public MethodRefPoolEntry(int classIndex, int nameAndTypeIndex) {
      this.classIndex = classIndex;
      this.nameAndTypeIndex = nameAndTypeIndex;
    }

    public void writeTo(OutputStream out) throws IOException {
      write1(out, CONSTANT_Methodref);
      write2(out, classIndex + 1);
      write2(out, nameAndTypeIndex + 1);
    }

    public boolean equals(Object o) {
      if (o instanceof MethodRefPoolEntry) {
        MethodRefPoolEntry other = (MethodRefPoolEntry) o;
        return other.classIndex == classIndex
          && other.nameAndTypeIndex == nameAndTypeIndex;
      } else {
        return false;
      }
    }
  }
}
