package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Boolean extends Object implements Comparable<Boolean>, Serializable {

    public static final Boolean TRUE = new Boolean(true);
    public static final Boolean FALSE = new Boolean(false);

    @SuppressWarnings("unchecked")
    public static final Class<Boolean> TYPE = (Class) java.lang.Boolean.TYPE;

    private final boolean value;

    public Boolean(boolean value) {
        this.value = value;
    }

    public Boolean(String s) {
        this(parseBoolean(s));
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Boolean) && ((Boolean) other).value == value;
    }

    @Override
    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(boolean value) {
        return java.lang.Boolean.hashCode(value);
    }

    public boolean booleanValue() {
        return value;
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Boolean.toString(value);
    }

    @Override
    @NotNull
    public String toDJVMString() {
        return toString(value);
    }

    public static String toString(boolean b) {
        return String.valueOf(b);
    }

    @Override
    @NotNull
    java.lang.Boolean fromDJVM() {
        return value;
    }

    @Override
    public int compareTo(@NotNull Boolean other) {
        return compare(value, other.value);
    }

    public static int compare(boolean x, boolean y) {
        return java.lang.Boolean.compare(x, y);
    }

    public static boolean parseBoolean(String s) {
        return java.lang.Boolean.parseBoolean(String.fromDJVM(s));
    }

    public static Boolean valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static Boolean valueOf(String s) {
        return valueOf(parseBoolean(s));
    }

    public static boolean logicalAnd(boolean a, boolean b) {
        return java.lang.Boolean.logicalAnd(a, b);
    }

    public static boolean logicalOr(boolean a, boolean b) {
        return java.lang.Boolean.logicalOr(a, b);
    }

    public static boolean logicalXor(boolean a, boolean b) {
        return java.lang.Boolean.logicalXor(a, b);
    }

    public static Boolean toDJVM(java.lang.Boolean b) { return (b == null) ? null : new Boolean(b); }
}
