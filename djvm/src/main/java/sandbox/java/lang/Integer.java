package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Integer extends Number implements Comparable<Integer> {

    public static final int MIN_VALUE = java.lang.Integer.MIN_VALUE;
    public static final int MAX_VALUE = java.lang.Integer.MAX_VALUE;
    public static final int BYTES = java.lang.Integer.BYTES;
    public static final int SIZE = java.lang.Integer.SIZE;

    static final int[] SIZE_TABLE = new int[] { 9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, MAX_VALUE };

    @SuppressWarnings("unchecked")
    public static final Class<Integer> TYPE = (Class) java.lang.Integer.TYPE;

    private final int value;

    public Integer(int value) {
        this.value = value;
    }

    public Integer(String s) throws NumberFormatException {
        this.value = parseInt(s, 10);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    public static int hashCode(int i) {
        return java.lang.Integer.hashCode(i);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Integer) && (value == ((Integer) other).value);
    }

    @Override
    public int intValue() {
        return value;
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
    public int compareTo(@NotNull Integer other) {
        return compare(this.value, other.value);
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Integer.toString(value);
    }

    @Override
    @NotNull
    java.lang.Integer fromDJVM() {
        return value;
    }

    public static String toString(int i, int radix) {
        return String.toDJVM(java.lang.Integer.toString(i, radix));
    }

    public static String toUnsignedString(int i, int radix) {
        return String.toDJVM(java.lang.Integer.toUnsignedString(i, radix));
    }

    public static String toHexString(int i) {
        return String.toDJVM(java.lang.Integer.toHexString(i));
    }

    public static String toOctalString(int i) {
        return String.toDJVM(java.lang.Integer.toOctalString(i));
    }

    public static String toBinaryString(int i) {
        return String.toDJVM(java.lang.Integer.toBinaryString(i));
    }

    public static String toString(int i) {
        return String.toDJVM(java.lang.Integer.toString(i));
    }

    public static String toUnsignedString(int i) {
        return String.toDJVM(java.lang.Integer.toUnsignedString(i));
    }

    public static int parseInt(String s, int radix) throws NumberFormatException {
        return java.lang.Integer.parseInt(String.fromDJVM(s), radix);
    }

    public static int parseInt(String s) throws NumberFormatException {
        return java.lang.Integer.parseInt(String.fromDJVM(s));
    }

    public static int parseUnsignedInt(String s, int radix) throws NumberFormatException {
        return java.lang.Integer.parseUnsignedInt(String.fromDJVM(s), radix);
    }

    public static int parseUnsignedInt(String s) throws NumberFormatException {
        return java.lang.Integer.parseUnsignedInt(String.fromDJVM(s));
    }

    public static Integer valueOf(String s, int radix) throws NumberFormatException {
        return toDJVM(java.lang.Integer.valueOf(String.fromDJVM(s), radix));
    }

    public static Integer valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Integer.valueOf(String.fromDJVM(s)));
    }

    public static Integer valueOf(int i) {
        return new Integer(i);
    }

    public static Integer decode(String nm) throws NumberFormatException {
        return new Integer(java.lang.Integer.decode(String.fromDJVM(nm)));
    }

    public static int compare(int x, int y) {
        return java.lang.Integer.compare(x, y);
    }

    public static int compareUnsigned(int x, int y) {
        return java.lang.Integer.compareUnsigned(x, y);
    }

    public static long toUnsignedLong(int x) {
        return java.lang.Integer.toUnsignedLong(x);
    }

    public static int divideUnsigned(int dividend, int divisor) {
        return java.lang.Integer.divideUnsigned(dividend, divisor);
    }

    public static int remainderUnsigned(int dividend, int divisor) {
        return java.lang.Integer.remainderUnsigned(dividend, divisor);
    }

    public static int highestOneBit(int i) {
        return java.lang.Integer.highestOneBit(i);
    }

    public static int lowestOneBit(int i) {
        return java.lang.Integer.lowestOneBit(i);
    }

    public static int numberOfLeadingZeros(int i) {
        return java.lang.Integer.numberOfLeadingZeros(i);
    }

    public static int numberOfTrailingZeros(int i) {
        return java.lang.Integer.numberOfTrailingZeros(i);
    }

    public static int bitCount(int i) {
        return java.lang.Integer.bitCount(i);
    }

    public static int rotateLeft(int i, int distance) {
        return java.lang.Integer.rotateLeft(i, distance);
    }

    public static int rotateRight(int i, int distance) {
        return java.lang.Integer.rotateRight(i, distance);
    }

    public static int reverse(int i) {
        return java.lang.Integer.reverse(i);
    }

    public static int signum(int i) {
        return java.lang.Integer.signum(i);
    }

    public static int reverseBytes(int i) {
        return java.lang.Integer.reverseBytes(i);
    }

    public static int sum(int a, int b) {
        return java.lang.Integer.sum(a, b);
    }

    public static int max(int a, int b) {
        return java.lang.Integer.max(a, b);
    }

    public static int min(int a, int b) {
        return java.lang.Integer.min(a, b);
    }

    public static Integer toDJVM(java.lang.Integer i) {
        return (i == null) ? null : valueOf(i);
    }

    static int stringSize(final int number) {
        int i = 0;
        while (number > SIZE_TABLE[i]) {
            ++i;
        }
        return i + 1;
    }

    static void getChars(final int number, int index, char[] buffer) {
        java.lang.String s = java.lang.Integer.toString(number);
        int length = s.length();

        while (length > 0) {
            buffer[--index] = s.charAt(--length);
        }
    }
}
