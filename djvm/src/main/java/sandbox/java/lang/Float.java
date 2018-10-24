package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Float extends Number implements Comparable<Float> {
    public static final float POSITIVE_INFINITY = java.lang.Float.POSITIVE_INFINITY;
    public static final float NEGATIVE_INFINITY = java.lang.Float.NEGATIVE_INFINITY;
    public static final float NaN = java.lang.Float.NaN;
    public static final float MAX_VALUE = java.lang.Float.MAX_VALUE;
    public static final float MIN_NORMAL = java.lang.Float.MIN_NORMAL;
    public static final float MIN_VALUE = java.lang.Float.MIN_VALUE;
    public static final int MAX_EXPONENT = java.lang.Float.MAX_EXPONENT;
    public static final int MIN_EXPONENT = java.lang.Float.MIN_EXPONENT;
    public static final int BYTES = java.lang.Float.BYTES;
    public static final int SIZE = java.lang.Float.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Float> TYPE = (Class) java.lang.Float.TYPE;

    private final float value;

    public Float(float value) {
        this.value = value;
    }

    public Float(String s) throws NumberFormatException {
        this.value = parseFloat(s);
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(float f) {
        return java.lang.Float.hashCode(f);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return other instanceof Float && floatToIntBits(((Float)other).value) == floatToIntBits(this.value);
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Float.toString(value);
    }

    @Override
    @NotNull
    java.lang.Float fromDJVM() {
        return value;
    }

    @Override
    public double doubleValue() {
        return (double)value;
    }

    @Override
    public float floatValue() {
        return value;
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

    @Override
    public int compareTo(@NotNull Float other) {
        return compare(this.value, other.value);
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    public static String toString(float f) {
        return String.valueOf(f);
    }

    public static String toHexString(float f) {
        return String.toDJVM(java.lang.Float.toHexString(f));
    }

    public static Float valueOf(String s) throws NumberFormatException {
        return toDJVM(java.lang.Float.valueOf(String.fromDJVM(s)));
    }

    public static Float valueOf(float f) {
        return new Float(f);
    }

    public static float parseFloat(String s) throws NumberFormatException {
        return java.lang.Float.parseFloat(String.fromDJVM(s));
    }

    public static boolean isNaN(float f) {
        return java.lang.Float.isNaN(f);
    }

    public static boolean isInfinite(float f) {
        return java.lang.Float.isInfinite(f);
    }

    public static boolean isFinite(float f) {
        return java.lang.Float.isFinite(f);
    }

    public static int floatToIntBits(float f) {
        return java.lang.Float.floatToIntBits(f);
    }

    public static int floatToRawIntBits(float f) {
        return java.lang.Float.floatToIntBits(f);
    }

    public static float intBitsToFloat(int bits) {
        return java.lang.Float.intBitsToFloat(bits);
    }

    public static int compare(float f1, float f2) {
        return java.lang.Float.compare(f1, f2);
    }

    public static float sum(float a, float b) {
        return java.lang.Float.sum(a, b);
    }

    public static float max(float a, float b) {
        return java.lang.Float.max(a, b);
    }

    public static float min(float a, float b) {
        return java.lang.Float.min(a, b);
    }

    public static Float toDJVM(java.lang.Float f) {
        return (f == null) ? null : valueOf(f);
    }
}
