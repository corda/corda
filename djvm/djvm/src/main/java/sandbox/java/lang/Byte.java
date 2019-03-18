package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Byte extends Number implements Comparable<Byte> {
    public static final byte MIN_VALUE = java.lang.Byte.MIN_VALUE;
    public static final byte MAX_VALUE = java.lang.Byte.MAX_VALUE;
    public static final int BYTES = java.lang.Byte.BYTES;
    public static final int SIZE = java.lang.Byte.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Byte> TYPE = (Class) java.lang.Byte.TYPE;

    private final byte value;

    public Byte(byte value) {
        this.value = value;
    }

    public Byte(String s) throws NumberFormatException {
        this.value = parseByte(s);
    }

    @Override
    public byte byteValue() {
        return value;
    }

    @Override
    public short shortValue() {
        return (short) value;
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return (double) value;
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(byte b) {
        return java.lang.Byte.hashCode(b);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Byte) && ((Byte) other).value == value;
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Byte.toString(value);
    }

    @Override
    @NotNull
    java.lang.Byte fromDJVM() {
        return value;
    }

    @Override
    public int compareTo(@NotNull Byte other) {
        return compare(this.value, other.value);
    }

    public static int compare(byte x, byte y) {
        return java.lang.Byte.compare(x, y);
    }

    public static String toString(byte b) {
        return Integer.toString(b);
    }

    public static Byte valueOf(byte b) {
        return new Byte(b);
    }

    public static byte parseByte(String s, int radix) throws NumberFormatException {
        return java.lang.Byte.parseByte(String.fromDJVM(s), radix);
    }

    public static byte parseByte(String s) throws NumberFormatException {
        return java.lang.Byte.parseByte(String.fromDJVM(s));
    }

    public static Byte valueOf(String s, int radix) throws NumberFormatException {
        return toDJVM(java.lang.Byte.valueOf(String.fromDJVM(s), radix));
    }

    public static Byte valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Byte.valueOf(String.fromDJVM(s)));
    }

    public static Byte decode(String s) throws NumberFormatException {
        return toDJVM(java.lang.Byte.decode(String.fromDJVM(s)));
    }

    public static int toUnsignedInt(byte b) {
        return java.lang.Byte.toUnsignedInt(b);
    }

    public static long toUnsignedLong(byte b) {
        return java.lang.Byte.toUnsignedLong(b);
    }

    public static Byte toDJVM(java.lang.Byte b) {
        return (b == null) ? null : valueOf(b);
    }
}
