/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang.reflect;

public class Field<T> extends AccessibleObject {
  private static final int VoidField = 0;
  private static final int ByteField = 1;
  private static final int CharField = 2;
  private static final int DoubleField = 3;
  private static final int FloatField = 4;
  private static final int IntField = 5;
  private static final int LongField = 6;
  private static final int ShortField = 7;
  private static final int BooleanField = 8;
  private static final int ObjectField = 9;

  private byte vmFlags;
  private byte code;
  private short flags;
  private short offset;
  private byte[] name;
  private byte[] spec;
  private Class<T> class_;

  private Field() { }

  public boolean isAccessible() {
    return (vmFlags & Accessible) != 0;
  }

  public void setAccessible(boolean v) {
    if (v) vmFlags |= Accessible; else vmFlags &= ~Accessible;
  }

  public Class<T> getDeclaringClass() {
    return class_;
  }

  public int getModifiers() {
    return flags;
  }

  public String getName() {
    return new String(name, 0, name.length - 1, false);
  }

  public Class getType() {
    return Class.forCanonicalName(new String(spec, 0, spec.length - 1, false));
  }

  public Object get(Object instance) throws IllegalAccessException {
    Object target;
    if ((flags & Modifier.STATIC) != 0) {
      target = class_.staticTable();
    } else if (class_.isInstance(instance)) {
      target = instance;
    } else {
      throw new IllegalArgumentException();
    }

    switch (code) {
    case ByteField:
      return Byte.valueOf((byte) getPrimitive(target, code, offset));

    case BooleanField:
      return Boolean.valueOf(getPrimitive(target, code, offset) != 0);

    case CharField:
      return Character.valueOf((char) getPrimitive(target, code, offset));

    case ShortField:
      return Short.valueOf((short) getPrimitive(target, code, offset));

    case IntField:
      return Integer.valueOf((int) getPrimitive(target, code, offset));

    case LongField:
      return Long.valueOf((int) getPrimitive(target, code, offset));

    case FloatField:
      return Float.valueOf
        (Float.intBitsToFloat((int) getPrimitive(target, code, offset)));

    case DoubleField:
      return Double.valueOf
        (Double.longBitsToDouble(getPrimitive(target, code, offset)));

    case ObjectField:
      return getObject(target, offset);

    default:
      throw new Error();
    }
  }

  public void set(Object instance, Object value)
    throws IllegalAccessException
  {
    Object target;
    if ((flags & Modifier.STATIC) != 0) {
      target = class_.staticTable();
    } else if (class_.isInstance(instance)) {
      target = instance;
    } else {
      throw new IllegalArgumentException();
    }

    switch (code) {
    case ByteField:
      setPrimitive(target, code, offset, (Byte) value);
      break;

    case BooleanField:
      setPrimitive(target, code, offset, ((Boolean) value) ? 1 : 0);
      break;

    case CharField:
      setPrimitive(target, code, offset, (Character) value);
      break;

    case ShortField:
      setPrimitive(target, code, offset, (Short) value);
      break;

    case IntField:
      setPrimitive(target, code, offset, (Integer) value);
      break;

    case LongField:
      setPrimitive(target, code, offset, (Long) value);
      break;

    case FloatField:
      setPrimitive(target, code, offset,
                   Float.floatToRawIntBits((Float) value));
      break;

    case DoubleField:
      setPrimitive(target, code, offset,
                   Double.doubleToRawLongBits((Double) value));
      break;

    case ObjectField:
      if (value == null || getType().isInstance(value)) {
        setObject(target, offset, value);
      } else {
        throw new IllegalArgumentException
          ("need " + getType() + ", got " + value.getClass().getName());
      }
      break;

    default:
      throw new Error();
    }
  }

  private static native long getPrimitive
    (Object instance, int code, int offset);

  private static native Object getObject
    (Object instance, int offset);

  private static native void setPrimitive
    (Object instance, int code, int offset, long value);

  private static native void setObject
    (Object instance, int offset, Object value);
}
