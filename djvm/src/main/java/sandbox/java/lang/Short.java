package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Short extends Number implements Comparable<Short> {
    public static final short MIN_VALUE = java.lang.Short.MIN_VALUE;
    public static final short MAX_VALUE = java.lang.Short.MAX_VALUE;
    public static final int BYTES = java.lang.Short.BYTES;
    public static final int SIZE = java.lang.Short.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Short> TYPE = (Class) java.lang.Short.TYPE;

    private final short value;

    public Short(short value) {
        this.value = value;
    }

    public Short(String s) throws NumberFormatException {
        this.value = parseShort(s);
    }

    @Override
    public byte byteValue() {
        return (byte)value;
    }

    @Override
    public short shortValue() {
        return value;
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public long longValue() {
        return (long)value;
    }

    @Override
    public float floatValue() {
        return (float)value;
    }

    @Override
    public double doubleValue() {
        return (double)value;
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Integer.toString(value);
    }

    @Override
    @NotNull
    java.lang.Short fromDJVM() {
        return value;
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(short value) {
        return java.lang.Short.hashCode(value);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Short) && ((Short) other).value == value;
    }

    public int compareTo(@NotNull Short other) {
        return compare(this.value, other.value);
    }

    public static int compare(short x, short y) {
        return java.lang.Short.compare(x, y);
    }

    public static short reverseBytes(short value) {
        return java.lang.Short.reverseBytes(value);
    }

    public static int toUnsignedInt(short x) {
        return java.lang.Short.toUnsignedInt(x);
    }

    public static long toUnsignedLong(short x) {
        return java.lang.Short.toUnsignedLong(x);
    }

    public static short parseShort(String s, int radix) throws NumberFormatException {
        return java.lang.Short.parseShort(String.fromDJVM(s), radix);
    }

    public static short parseShort(String s) throws NumberFormatException {
        return java.lang.Short.parseShort(String.fromDJVM(s));
    }

    public static Short valueOf(String s, int radix) throws NumberFormatException {
        return toDJVM(java.lang.Short.valueOf(String.fromDJVM(s), radix));
    }

    public static Short valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Short.valueOf(String.fromDJVM(s)));
    }

    public static Short valueOf(short s) {
        return new Short(s);
    }

    public static Short decode(String nm) throws NumberFormatException {
        return toDJVM(java.lang.Short.decode(String.fromDJVM(nm)));
    }

    public static Short toDJVM(java.lang.Short i) {
        return (i == null) ? null : valueOf(i);
    }
}