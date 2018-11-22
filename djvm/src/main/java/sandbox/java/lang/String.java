package sandbox.java.lang;

import net.corda.djvm.SandboxRuntimeContext;
import org.jetbrains.annotations.NotNull;
import sandbox.java.nio.charset.Charset;
import sandbox.java.util.Comparator;
import sandbox.java.util.Locale;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;

@SuppressWarnings("unused")
public final class String extends Object implements Comparable<String>, CharSequence, Serializable {
    public static final Comparator<String> CASE_INSENSITIVE_ORDER = new CaseInsensitiveComparator();

    private static class CaseInsensitiveComparator extends Object implements Comparator<String>, Serializable {
        @Override
        public int compare(String s1, String s2) {
            return java.lang.String.CASE_INSENSITIVE_ORDER.compare(String.fromDJVM(s1), String.fromDJVM(s2));
        }
    }

    private static final String TRUE = new String("true");
    private static final String FALSE = new String("false");

    private static final Constructor SHARED;

    static {
        try {
            SHARED = java.lang.String.class.getDeclaredConstructor(char[].class, java.lang.Boolean.TYPE);
            SHARED.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    private final java.lang.String value;

    public String() {
        this.value = "";
    }

    public String(java.lang.String value) {
        this.value = value;
    }

    public String(char value[]) {
        this.value = new java.lang.String(value);
    }

    public String(char value[], int offset, int count) {
        this.value = new java.lang.String(value, offset, count);
    }

    public String(int[] codePoints, int offset, int count) {
        this.value = new java.lang.String(codePoints, offset, count);
    }

    @Deprecated
    public String(byte ascii[], int hibyte, int offset, int count) {
        this.value = new java.lang.String(ascii, hibyte, offset, count);
    }

    @Deprecated
    public String(byte ascii[], int hibyte) {
        this.value = new java.lang.String(ascii, hibyte);
    }

    public String(byte bytes[], int offset, int length, String charsetName)
            throws UnsupportedEncodingException {
        this.value = new java.lang.String(bytes, offset, length, fromDJVM(charsetName));
    }

    public String(byte bytes[], int offset, int length, Charset charset) {
        this.value = new java.lang.String(bytes, offset, length, fromDJVM(charset));
    }

    public String(byte bytes[], String charsetName)
            throws UnsupportedEncodingException {
        this.value = new java.lang.String(bytes, fromDJVM(charsetName));
    }

    public String(byte bytes[], Charset charset) {
        this.value = new java.lang.String(bytes, fromDJVM(charset));
    }

    public String(byte bytes[], int offset, int length) {
        this.value = new java.lang.String(bytes, offset, length);
    }

    public String(byte bytes[]) {
        this.value = new java.lang.String(bytes);
    }

    public String(StringBuffer buffer) {
        this.value = buffer.toString();
    }

    public String(StringBuilder builder) {
        this.value = builder.toString();
    }

    String(char[] value, boolean share) {
        java.lang.String newValue;
        try {
            // This is (presumably) an optimisation for memory usage.
            newValue = (java.lang.String) SHARED.newInstance(value, share);
        } catch (Exception e) {
            newValue = new java.lang.String(value);
        }
        this.value = newValue;
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public int length() {
        return value.length();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public int codePointAt(int index) {
        return value.codePointAt(index);
    }

    public int codePointBefore(int index) {
        return value.codePointBefore(index);
    }

    public int codePointCount(int beginIndex, int endIndex) {
        return value.codePointCount(beginIndex, endIndex);
    }

    public int offsetByCodePoints(int index, int codePointOffset) {
        return value.offsetByCodePoints(index, codePointOffset);
    }

    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        value.getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    @Deprecated
    public void getBytes(int srcBegin, int srcEnd, byte dst[], int dstBegin) {
        value.getBytes(srcBegin, srcEnd, dst, dstBegin);
    }

    public byte[] getBytes(String charsetName) throws UnsupportedEncodingException {
        return value.getBytes(fromDJVM(charsetName));
    }

    public byte[] getBytes(Charset charset) {
        return value.getBytes(fromDJVM(charset));
    }

    public byte[] getBytes() {
        return value.getBytes();
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof String) && ((String) other).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return value;
    }

    @Override
    @NotNull
    public String toDJVMString() {
        return this;
    }

    @Override
    @NotNull
    java.lang.String fromDJVM() {
        return value;
    }

    public boolean contentEquals(StringBuffer sb) {
        return value.contentEquals((CharSequence) sb);
    }

    public boolean contentEquals(CharSequence cs) {
        return value.contentEquals(cs);
    }

    public boolean equalsIgnoreCase(String anotherString) {
        return value.equalsIgnoreCase(fromDJVM(anotherString));
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return toDJVM((java.lang.String) value.subSequence(start, end));
    }

    @Override
    public int compareTo(@NotNull String other) {
        return value.compareTo(other.toString());
    }

    public int compareToIgnoreCase(String str) {
        return value.compareToIgnoreCase(fromDJVM(str));
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        return value.regionMatches(toffset, fromDJVM(other), ooffset, len);
    }

    public boolean regionMatches(boolean ignoreCase, int toffset,
                                 String other, int ooffset, int len) {
        return value.regionMatches(ignoreCase, toffset, fromDJVM(other), ooffset, len);
    }

    public boolean startsWith(String prefix, int toffset) {
        return value.startsWith(fromDJVM(prefix), toffset);
    }

    public boolean startsWith(String prefix) {
        return value.startsWith(fromDJVM(prefix));
    }

    public boolean endsWith(String suffix) {
        return value.endsWith(fromDJVM(suffix));
    }

    public int indexOf(int ch) {
        return value.indexOf(ch);
    }

    public int indexOf(int ch, int fromIndex) {
        return value.indexOf(ch, fromIndex);
    }

    public int lastIndexOf(int ch) {
        return value.lastIndexOf(ch);
    }

    public int lastIndexOf(int ch, int fromIndex) {
        return value.lastIndexOf(ch, fromIndex);
    }

    public int indexOf(String str) {
        return value.indexOf(fromDJVM(str));
    }

    public int indexOf(String str, int fromIndex) {
        return value.indexOf(fromDJVM(str), fromIndex);
    }

    public int lastIndexOf(String str) {
        return value.lastIndexOf(fromDJVM(str));
    }

    public int lastIndexOf(String str, int fromIndex) {
        return value.lastIndexOf(fromDJVM(str), fromIndex);
    }

    public String substring(int beginIndex) {
        return toDJVM(value.substring(beginIndex));
    }

    public String substring(int beginIndex, int endIndex) {
        return toDJVM(value.substring(beginIndex, endIndex));
    }

    public String concat(String str) {
        return toDJVM(value.concat(fromDJVM(str)));
    }

    public String replace(char oldChar, char newChar) {
        return toDJVM(value.replace(oldChar, newChar));
    }

    public boolean matches(String regex) {
        return value.matches(fromDJVM(regex));
    }

    public boolean contains(CharSequence s) {
        return value.contains(s);
    }

    public String replaceFirst(String regex, String replacement) {
        return toDJVM(value.replaceFirst(fromDJVM(regex), fromDJVM(replacement)));
    }

    public String replaceAll(String regex, String replacement) {
        return toDJVM(value.replaceAll(fromDJVM(regex), fromDJVM(replacement)));
    }

    public String replace(CharSequence target, CharSequence replacement) {
        return toDJVM(value.replace(target, replacement));
    }

    public String[] split(String regex, int limit) {
        return toDJVM(value.split(fromDJVM(regex), limit));
    }

    public String[] split(String regex) {
        return toDJVM(value.split(fromDJVM(regex)));
    }

    public String toLowerCase(Locale locale) {
        return toDJVM(value.toLowerCase(fromDJVM(locale)));
    }

    public String toLowerCase() {
        return toDJVM(value.toLowerCase());
    }

    public String toUpperCase(Locale locale) {
        return toDJVM(value.toUpperCase(fromDJVM(locale)));
    }

    public String toUpperCase() {
        return toDJVM(value.toUpperCase());
    }

    public String trim() {
        return toDJVM(value.trim());
    }

    public String intern() { return (String) SandboxRuntimeContext.getInstance().intern(value, this); }

    public char[] toCharArray() {
        return value.toCharArray();
    }

    public static String format(String format, java.lang.Object... args) {
        return toDJVM(java.lang.String.format(fromDJVM(format), fromDJVM(args)));
    }

    public static String format(Locale locale, String format, java.lang.Object... args) {
        return toDJVM(java.lang.String.format(fromDJVM(locale), fromDJVM(format), fromDJVM(args)));
    }

    public static String join(CharSequence delimiter, CharSequence... elements) {
        return toDJVM(java.lang.String.join(delimiter, elements));
    }

    public static String join(CharSequence delimiter,
                              Iterable<? extends CharSequence> elements) {
        return toDJVM(java.lang.String.join(delimiter, elements));
    }

    public static String valueOf(java.lang.Object obj) {
        return (obj instanceof Object) ? ((Object) obj).toDJVMString() : toDJVM(java.lang.String.valueOf(obj));
    }

    public static String valueOf(char data[]) {
        return toDJVM(java.lang.String.valueOf(data));
    }

    public static String valueOf(char data[], int offset, int count) {
        return toDJVM(java.lang.String.valueOf(data, offset, count));
    }

    public static String copyValueOf(char data[], int offset, int count) {
        return toDJVM(java.lang.String.copyValueOf(data, offset, count));
    }

    public static String copyValueOf(char data[]) {
        return toDJVM(java.lang.String.copyValueOf(data));
    }

    public static String valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static String valueOf(char c) {
        return toDJVM(java.lang.String.valueOf(c));
    }

    public static String valueOf(int i) {
        return toDJVM(java.lang.String.valueOf(i));
    }

    public static String valueOf(long l) {
        return toDJVM(java.lang.String.valueOf(l));
    }

    public static String valueOf(float f) {
        return toDJVM(java.lang.String.valueOf(f));
    }

    public static String valueOf(double d) {
        return toDJVM(java.lang.String.valueOf(d));
    }

    static String[] toDJVM(java.lang.String[] value) {
        if (value == null) {
            return null;
        }
        String[] result = new String[value.length];
        int i = 0;
        for (java.lang.String v : value) {
            result[i] = toDJVM(v);
            ++i;
        }
        return result;
    }

    public static String toDJVM(java.lang.String value) {
        return (value == null) ? null : new String(value);
    }

    public static java.lang.String fromDJVM(String value) {
        return (value == null) ? null : value.fromDJVM();
    }
}