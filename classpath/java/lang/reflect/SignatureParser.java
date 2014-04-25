/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

import java.util.ArrayList;
import java.util.List;

class SignatureParser {
  private final ClassLoader loader;
  private final char[] array;
  private int offset;
  private final Type type;

  static Type parse(ClassLoader loader, String signature) {
    return new SignatureParser(loader, signature).type;
  }

  private SignatureParser(ClassLoader loader, String signature) {
    this.loader = loader;
    array = signature.toCharArray();
    type = parseType();
    if (offset != array.length) {
      throw new IllegalArgumentException("Extra characters after " + offset
          + ": " + signature);
    }
  }

  private Type parseType() {
    char c = array[offset++];
    if (c == 'B') {
      return Byte.TYPE;
    } else if (c == 'C') {
      return Character.TYPE;
    } else if (c == 'D') {
      return Double.TYPE;
    } else if (c == 'F') {
      return Float.TYPE;
    } else if (c == 'I') {
      return Integer.TYPE;
    } else if (c == 'J') {
      return Long.TYPE;
    } else if (c == 'S') {
      return Short.TYPE;
    } else if (c == 'Z') {
      return Boolean.TYPE;
    } else if (c != 'L') {
      throw new IllegalArgumentException("Unexpected character: " + c);
    }
    StringBuilder builder = new StringBuilder();
    Type ownerType = null;
    for (;;) {
      for (;;) {
        c = array[offset++];
        if (c == ';' || c == '<') {
          break;
        }
        builder.append(c == '/' ? '.' : c);
      }
      String rawTypeName = builder.toString();
      Class<?> rawType;
      try {
        rawType = loader.loadClass(rawTypeName);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Could not find class " + rawTypeName);
      }
      if (c == ';') {
        return rawType;
      }
      List<Type> args = new ArrayList<Type>();
      while (array[offset] != '>') {
        args.add(parseType());
      }
      ++offset;
      c = array[offset++];
      ParameterizedType type = makeType(args.toArray(new Type[args.size()]), ownerType, rawType);
      if (c == ';') {
        return type;
      }
      if (c != '.') {
        throw new RuntimeException("TODO");
      }
      ownerType = type;
      builder.append("$");
    }
  }

  private static String typeName(Type type) {
    if (type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      return clazz.getName();
    }
    return type.toString();
  }

  private static ParameterizedType makeType(final Type[] args, final Type owner, final Type raw) {
    return new ParameterizedType() {
      @Override
        public Type getRawType() {
          return raw;
        }

      @Override
        public Type getOwnerType() {
          return owner;
        }

      @Override
        public Type[] getActualTypeArguments() {
          return args;
        }

      @Override
        public String toString() {
          StringBuilder builder = new StringBuilder();
          builder.append(typeName(raw));
          builder.append('<');
          String sep = "";
          for (Type t : args) {
            builder.append(sep).append(typeName(t));
            sep = ", ";
          }
          builder.append('>');
          return builder.toString();
        }
    };
  }
}
