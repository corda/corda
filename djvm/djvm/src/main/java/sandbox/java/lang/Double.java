package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Double extends Number implements Comparable<Double> {
    public static final double POSITIVE_INFINITY = java.lang.Double.POSITIVE_INFINITY;
    public static final double NEGATIVE_INFINITY = java.lang.Double.NEGATIVE_INFINITY;
    public static final double NaN = java.lang.Double.NaN;
    public static final double MAX_VALUE = java.lang.Double.MAX_VALUE;
    public static final double MIN_NORMAL = java.lang.Double.MIN_NORMAL;
    public static final double MIN_VALUE = java.lang.Double.MIN_VALUE;
    public static final int MAX_EXPONENT = java.lang.Double.MAX_EXPONENT;
    public static final int MIN_EXPONENT = java.lang.Double.MIN_EXPONENT;
    public static final int BYTES = java.lang.Double.BYTES;
    public static final int SIZE = java.lang.Double.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Double> TYPE = (Class) java.lang.Double.TYPE;

    private final double value;

    public Double(double value) {
        this.value = value;
    }

    public Double(String s) throws NumberFormatException {
        this.value = parseDouble(s);
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return (float)value;
    }

    @Override
    public long longValue() {
        return (long)value;
    }

    @Override
    public int intValue() {
        return (int)value;
    }

    @Override
    public short shortValue() {
        return (short)value;
    }

    @Override
    public byte byteValue() {
        return (byte)value;
    }

    public boolean isNaN() {
        return java.lang.Double.isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(this.value);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Double) && doubleToLongBits(((Double)other).value) == doubleToLongBits(value);
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(double d) {
        return java.lang.Double.hashCode(d);
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Double.toString(value);
    }

    @Override
    @NotNull
    java.lang.Double fromDJVM() {
        return value;
    }

    @Override
    public int compareTo(@NotNull Double other) {
        return compare(this.value, other.value);
    }

    public static String toString(double d) {
        return String.toDJVM(java.lang.Double.toString(d));
    }

    public static String toHexString(double d) {
        return String.toDJVM(java.lang.Double.toHexString(d));
    }

    public static Double valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Double.valueOf(String.fromDJVM(s)));
    }

    public static Double valueOf(double d) {
        return new Double(d);
    }

    public static double parseDouble(String s) throws NumberFormatException {
        return java.lang.Double.parseDouble(String.fromDJVM(s));
    }

    public static boolean isNaN(double d) {
        return java.lang.Double.isNaN(d);
    }

    public static boolean isInfinite(double d) {
        return java.lang.Double.isInfinite(d);
    }

    public static boolean isFinite(double d) {
        return java.lang.Double.isFinite(d);
    }

    public static long doubleToLongBits(double d) {
        return java.lang.Double.doubleToLongBits(d);
    }

    public static long doubleToRawLongBits(double d) {
        return java.lang.Double.doubleToRawLongBits(d);
    }

    public static double longBitsToDouble(long bits) {
        return java.lang.Double.longBitsToDouble(bits);
    }

    public static int compare(double d1, double d2) {
        return java.lang.Double.compare(d1, d2);
    }

    public static double sum(double a, double b) {
        return java.lang.Double.sum(a, b);
    }

    public static double max(double a, double b) {
        return java.lang.Double.max(a, b);
    }

    public static double min(double a, double b) {
        return java.lang.Double.min(a, b);
    }

    public static Double toDJVM(java.lang.Double d) {
        return (d == null) ? null : valueOf(d);
    }
}
