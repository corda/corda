/* Copyright (c) 2008-2016, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.invoke;

import static avian.Assembler.*;

import avian.Assembler;
import avian.Classes;
import avian.SystemClassLoader;
import avian.VMClass;

import java.util.List;
import java.util.ArrayList;

public final class MethodType implements java.io.Serializable {
  private static final char[] Primitives = new char[] {
    'V', 'Z', 'B', 'C', 'S', 'I', 'F', 'J', 'D'
  };

  final ClassLoader loader;
  final byte[] spec;
  private volatile List<Parameter> parameters;
  private volatile Result result;
  private volatile int footprint;

  MethodType(ClassLoader loader, byte[] spec) {
    this.loader = loader;
    this.spec = spec;
  }

  MethodType(String spec) {
    this.loader = SystemClassLoader.appLoader();
    try {
        this.spec = spec.getBytes("UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
        throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public String toMethodDescriptorString() {
    return Classes.makeString(spec, 0, spec.length - 1);
  }

  private static String spec(Class c) {
    if (c.isPrimitive()) {
      VMClass vmc = Classes.toVMClass(c);
      for (char p: Primitives) {
        if (vmc == Classes.primitiveClass(p)) {
          return String.valueOf(p);
        }
      }
      throw new AssertionError();
    } else if (c.isArray()) {
      return "[" + spec(c.getComponentType());
    } else {
      return "L" + c.getName().replace('.', '/') + ";";
    }
  }

  private MethodType(Class rtype,
                     Class ... ptypes)
  {
    loader = rtype.getClassLoader();

    StringBuilder sb = new StringBuilder();
    sb.append('(');
    parameters = new ArrayList(ptypes.length);
    int position = 0;
    for (int i = 0; i < ptypes.length; ++i) {
      String spec = spec(ptypes[i]);
      sb.append(spec);

      Type type = type(spec);

      parameters.add(new Parameter(i,
                                   position,
                                   spec,
                                   ptypes[i],
                                   type.load));

      position += type.size;
    }
    sb.append(')');

    footprint = position;

    String spec = spec(rtype);
    sb.append(spec);

    result = new Result(spec, rtype, type(spec).return_);

    try {
        this.spec = sb.toString().getBytes("UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
        throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public static MethodType methodType(Class rtype,
                                      Class ptype0,
                                      Class ... ptypes)
  {
    Class[] array = new Class[ptypes.length + 1];
    array[0] = ptype0;
    System.arraycopy(ptypes, 0, array, 1, ptypes.length);
    return methodType(rtype, array);
  }

  public static MethodType methodType(Class rtype,
                                      Class ... ptypes)
  {
    return new MethodType(rtype, ptypes);
  }

  public String toString() {
    return Classes.makeString(spec, 0, spec.length - 1);
  }

  public int footprint() {
    parameters(); // ensure spec is parsed

    return footprint;
  }

  public Class returnType() {
    parameters(); // ensure spec is parsed

    return result.type;
  }

  public Class[] parameterArray() {
    parameters(); // ensure spec is parsed

    Class[] array = new Class[parameters.size()];
    for (int i = 0; i < parameters.size(); ++i) {
      array[i] = parameters.get(i).type;
    }

    return array;
  }

  public Iterable<Parameter> parameters() {
    if (parameters == null) {
      List<Parameter> list = new ArrayList();
      int i;
      int index = 0;
      int position = 0;
      for (i = 1; spec[i] != ')'; ++i) {
        int start = i;
        switch (spec[i]) {
        case 'L': {
          ++ i;
          while (spec[i] != ';') ++ i;
        } break;

        case '[': {
          ++ i;
          while (spec[i] == '[') ++ i;

          switch (spec[i]) {
          case 'L':
            ++ i;
            while (spec[i] != ';') ++ i;
            break;

          default:
            break;
          }
        } break;

        case 'Z':
        case 'B':
        case 'S':
        case 'C':
        case 'I':
        case 'F':
        case 'J':
        case 'D':
          break;

        default: throw new AssertionError();
        }

        String paramSpec = Classes.makeString(spec, start, (i - start) + 1);
        Type type = type(paramSpec);

        list.add(new Parameter
                 (index,
                  position,
                  paramSpec,
                  Classes.forCanonicalName(loader, paramSpec),
                  type.load));

        ++ index;
        position += type.size;
      }

      footprint = position;

      ++ i;

      String paramSpec = Classes.makeString(spec, i, spec.length - i - 1);
      Type type = type(paramSpec);

      result = new Result(paramSpec,
                          Classes.forCanonicalName(loader, paramSpec),
                          type.return_);

      parameters = list;
    }

    return parameters;
  }

  public Result result() {
    parameters(); // ensure spec has been parsed

    return result;
  }

  private static Type type(String spec) {
    switch (spec.charAt(0)) {
    case 'L':
    case '[':
      return Type.ObjectType;

    case 'Z':
    case 'B':
    case 'S':
    case 'C':
    case 'I':
      return Type.IntegerType;

    case 'F':
      return Type.FloatType;

    case 'J':
      return Type.LongType;

    case 'D':
      return Type.DoubleType;

    case 'V':
      return Type.VoidType;

    default: throw new AssertionError();
    }
  }

  private static enum Type {
    ObjectType(aload, areturn, 1),
    IntegerType(iload, ireturn, 1),
    FloatType(fload, freturn, 1),
    LongType(lload, lreturn, 2),
    DoubleType(dload, dreturn, 2),
    VoidType(-1, Assembler.return_, -1);

    public final int load;
    public final int return_;
    public final int size;

    private Type(int load, int return_, int size) {
      this.load = load;
      this.return_ = return_;
      this.size = size;
    }
  }

  public interface TypeSpec {
    public Class type();

    public String spec();
  }

  public static class Parameter implements TypeSpec {
    private final int index;
    private final int position;
    private final String spec;
    private final Class type;
    private final int load;

    private Parameter(int index,
                      int position,
                      String spec,
                      Class type,
                      int load)
    {
      this.index = index;
      this.position = position;
      this.spec = spec;
      this.type = type;
      this.load = load;
    }

    public int index() {
      return index;
    }

    public int position() {
      return position;
    }

    public String spec() {
      return spec;
    }

    public Class type() {
      return type;
    }

    public int load() {
      return load;
    }
  }

  public static class Result implements TypeSpec {
    private final String spec;
    private final Class type;
    private final int return_;

    public Result(String spec, Class type, int return_) {
      this.spec = spec;
      this.type = type;
      this.return_ = return_;
    }

    public int return_() {
      return return_; // :)
    }

    public String spec() {
      return spec;
    }

    public Class type() {
      return type;
    }
  }
}
