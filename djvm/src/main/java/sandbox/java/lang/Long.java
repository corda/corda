package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Long extends Number implements Comparable<Long> {

    public static final long MIN_VALUE = java.lang.Long.MIN_VALUE;
    public static final long MAX_VALUE = java.lang.Long.MAX_VALUE;
    public static final int BYTES = java.lang.Long.BYTES;
    public static final int SIZE = java.lang.Long.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Long> TYPE = (Class) java.lang.Long.TYPE;

    private final long value;

    public Long(long value) {
        this.value = value;
    }

    public Long(String s) throws NumberFormatException {
        this.value = parseLong(s, 10);
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Long) && ((Long) other).longValue() == value;
    }

    public static int hashCode(long l) {
        return java.lang.Long.hashCode(l);
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public short shortValue() {
        return (short) value;
    }

    @Override
    public byte byteValue() {
        return (byte) value;
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
    public int compareTo(@NotNull Long other) {
        return compare(value, other.value);
    }

    public static int compare(long x, long y) {
        return java.lang.Long.compare(x, y);
    }

    @Override
    @NotNull
    java.lang.Long fromDJVM() {
        return value;
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Long.toString(value);
    }

    public static String toString(long l) {
        return String.toDJVM(java.lang.Long.toString(l));
    }

    public static String toString(long l, int radix) {
        return String.toDJVM(java.lang.Long.toString(l, radix));
    }

    public static String toUnsignedString(long l, int radix) {
        return String.toDJVM(java.lang.Long.toUnsignedString(l, radix));
    }

    public static String toUnsignedString(long l) {
        return String.toDJVM(java.lang.Long.toUnsignedString(l));
    }

    public static String toHexString(long l) {
        return String.toDJVM(java.lang.Long.toHexString(l));
    }

    public static String toOctalString(long l) {
        return String.toDJVM(java.lang.Long.toOctalString(l));
    }

    public static String toBinaryString(long l) {
        return String.toDJVM(java.lang.Long.toBinaryString(l));
    }

    public static long parseLong(String s, int radix) throws NumberFormatException {
        return java.lang.Long.parseLong(String.fromDJVM(s), radix);
    }

    public static long parseLong(String s) throws NumberFormatException {
        return java.lang.Long.parseLong(String.fromDJVM(s));
    }

    public static long parseUnsignedLong(String s, int radix) throws NumberFormatException {
        return java.lang.Long.parseUnsignedLong(String.fromDJVM(s), radix);
    }

    public static long parseUnsignedLong(String s) throws NumberFormatException {
        return java.lang.Long.parseUnsignedLong(String.fromDJVM(s));
    }

    public static Long valueOf(String s, int radix) throws NumberFormatException {
        return toDJVM(java.lang.Long.valueOf(String.fromDJVM(s), radix));
    }

    public static Long valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Long.valueOf(String.fromDJVM(s)));
    }

    public static Long valueOf(long l) {
        return new Long(l);
    }

    public static Long decode(String s) throws NumberFormatException {
        return toDJVM(java.lang.Long.decode(String.fromDJVM(s)));
    }

    public static int compareUnsigned(long x, long y) {
        return java.lang.Long.compareUnsigned(x, y);
    }

    public static long divideUnsigned(long dividend, long divisor) {
        return java.lang.Long.divideUnsigned(dividend, divisor);
    }

    public static long remainderUnsigned(long dividend, long divisor) {
        return java.lang.Long.remainderUnsigned(dividend, divisor);
    }

    public static long highestOneBit(long l) {
        return java.lang.Long.highestOneBit(l);
    }

    public static long lowestOneBit(long l) {
        return java.lang.Long.lowestOneBit(l);
    }

    public static int numberOfLeadingZeros(long l) {
        return java.lang.Long.numberOfLeadingZeros(l);
    }

    public static int numberOfTrailingZeros(long l) {
        return java.lang.Long.numberOfTrailingZeros(l);
    }

    public static int bitCount(long l) {
        return java.lang.Long.bitCount(l);
    }

    public static long rotateLeft(long i, int distance) {
        return java.lang.Long.rotateLeft(i, distance);
    }

    public static long rotateRight(long i, int distance) {
        return java.lang.Long.rotateRight(i, distance);
    }

    public static long reverse(long l) {
        return java.lang.Long.reverse(l);
    }

    public static int signum(long l) {
        return java.lang.Long.signum(l);
    }

    public static long reverseBytes(long l) {
        return java.lang.Long.reverseBytes(l);
    }

    public static long sum(long a, long b) {
        return java.lang.Long.sum(a, b);
    }

    public static long max(long a, long b) {
        return java.lang.Long.max(a, b);
    }

    public static long min(long a, long b) {
        return java.lang.Long.min(a, b);
    }

    public static Long toDJVM(java.lang.Long l) {
        return (l == null) ? null : valueOf(l);
    }

    static int stringSize(final long number) {
        long l = 10;
        int i = 1;

        while ((i < 19) && (number >= l)) {
            l *= 10;
            ++i;
        }

        return i;
    }

    static void getChars(final long number, int index, char[] buffer) {
        java.lang.String s = java.lang.Long.toString(number);
        int length = s.length();

        while (length > 0) {
            buffer[--index] = s.charAt(--length);
        }
    }
}
