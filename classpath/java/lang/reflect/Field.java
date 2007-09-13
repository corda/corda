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
    return Class.forCanonicalName(getName());
  }

  public Object get(Object instance) throws IllegalAccessException {
    if ((flags & Modifier.STATIC) != 0) {
      Object v = class_.staticTable()[offset];
      switch (code) {
      case ByteField:
        return Byte.valueOf
          ((byte) (v == null ? 0 : ((Integer) v).intValue()));

      case BooleanField:
        return Boolean.valueOf
          (v == null ? false : ((Integer) v) != 0);

      case CharField:
        return Character.valueOf
          ((char) (v == null ? 0 : ((Integer) v).intValue()));

      case ShortField:
        return Short.valueOf
          ((short) (v == null ? 0 : ((Integer) v).intValue()));

      case FloatField:
        return Float.valueOf
          (Float.intBitsToFloat(v == null ? 0 : (Integer) v));

      case DoubleField:
        return Double.valueOf
          (Double.longBitsToDouble(v == null ? 0 : (Long) v));

      case IntField:
      case LongField:
      case ObjectField:
        return v;

      default:
        throw new Error();
      }
    } else if (class_.isInstance(instance)) {
      switch (code) {
      case ByteField:
        return Byte.valueOf((byte) getPrimitive(instance, code, offset));

      case BooleanField:
        return Boolean.valueOf(getPrimitive(instance, code, offset) != 0);

      case CharField:
        return Character.valueOf((char) getPrimitive(instance, code, offset));

      case ShortField:
        return Short.valueOf((short) getPrimitive(instance, code, offset));

      case IntField:
        return Integer.valueOf((int) getPrimitive(instance, code, offset));

      case LongField:
        return Long.valueOf((int) getPrimitive(instance, code, offset));

      case FloatField:
        return Float.valueOf
          (Float.intBitsToFloat((int) getPrimitive(instance, code, offset)));

      case DoubleField:
        return Double.valueOf
          (Double.longBitsToDouble(getPrimitive(instance, code, offset)));

      case ObjectField:
        return getObject(instance, offset);

      default:
        throw new Error();
      }        
    } else {
      throw new IllegalArgumentException();
    }
  }

  public void set(Object instance, Object value)
    throws IllegalAccessException
  {
    if ((flags & Modifier.STATIC) != 0) {
      Object[] a = class_.staticTable();
      switch (code) {
      case ByteField:
        a[offset] = Integer.valueOf((Byte) value);
        break;

      case BooleanField:
        a[offset] = Integer.valueOf(((Boolean) value) ? 1 : 0);
        break;

      case CharField:
        a[offset] = Integer.valueOf((Character) value);
        break;

      case ShortField:
        a[offset] = Integer.valueOf((Short) value);
        break;

      case FloatField:
        a[offset] = Integer.valueOf(Float.floatToRawIntBits((Float) value));
        break;

      case DoubleField:
        a[offset] = Long.valueOf(Double.doubleToRawLongBits((Double) value));
        break;

      case IntField:
      case LongField:
        a[offset] = value;
        break;

      case ObjectField:
        if (getType().isInstance(value)) {
          a[offset] = value;
        } else {
          throw new IllegalArgumentException();
        }
        break;

      default:
        throw new Error();
      }
    } else if (class_.isInstance(instance)) {
      switch (code) {
      case ByteField:
        setPrimitive(instance, code, offset, (Byte) value);
        break;

      case BooleanField:
        setPrimitive(instance, code, offset, ((Boolean) value) ? 1 : 0);
        break;

      case CharField:
        setPrimitive(instance, code, offset, (Character) value);
        break;

      case ShortField:
        setPrimitive(instance, code, offset, (Short) value);
        break;

      case IntField:
        setPrimitive(instance, code, offset, (Integer) value);
        break;

      case LongField:
        setPrimitive(instance, code, offset, (Long) value);
        break;

      case FloatField:
        setPrimitive(instance, code, offset,
                     Float.floatToRawIntBits((Float) value));
        break;

      case DoubleField:
        setPrimitive(instance, code, offset,
                     Double.doubleToRawLongBits((Double) value));
        break;

      case ObjectField:
        if (getType().isInstance(value)) {
          setObject(instance, offset, value);
        } else {
          throw new IllegalArgumentException();
        }
        break;

      default:
        throw new Error();
      }        
    } else {
      throw new IllegalArgumentException();
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
