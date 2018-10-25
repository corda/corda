package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Character extends Object implements Comparable<Character>, Serializable {
    public static final int MIN_RADIX = java.lang.Character.MIN_RADIX;
    public static final int MAX_RADIX = java.lang.Character.MAX_RADIX;
    public static final char MIN_VALUE = java.lang.Character.MIN_VALUE;
    public static final char MAX_VALUE = java.lang.Character.MAX_VALUE;

    @SuppressWarnings("unchecked")
    public static final Class<Character> TYPE = (Class) java.lang.Character.TYPE;

    public static final byte UNASSIGNED = java.lang.Character.UNASSIGNED;
    public static final byte UPPERCASE_LETTER = java.lang.Character.UPPERCASE_LETTER;
    public static final byte LOWERCASE_LETTER = java.lang.Character.LOWERCASE_LETTER;
    public static final byte TITLECASE_LETTER = java.lang.Character.TITLECASE_LETTER;
    public static final byte MODIFIER_LETTER = java.lang.Character.MODIFIER_LETTER;
    public static final byte OTHER_LETTER = java.lang.Character.OTHER_LETTER;
    public static final byte NON_SPACING_MARK = java.lang.Character.NON_SPACING_MARK;
    public static final byte ENCLOSING_MARK = java.lang.Character.ENCLOSING_MARK;
    public static final byte COMBINING_SPACING_MARK = java.lang.Character.COMBINING_SPACING_MARK;
    public static final byte DECIMAL_DIGIT_NUMBER = java.lang.Character.DECIMAL_DIGIT_NUMBER;
    public static final byte LETTER_NUMBER = java.lang.Character.LETTER_NUMBER;
    public static final byte OTHER_NUMBER = java.lang.Character.OTHER_NUMBER;
    public static final byte SPACE_SEPARATOR = java.lang.Character.SPACE_SEPARATOR;
    public static final byte LINE_SEPARATOR = java.lang.Character.LINE_SEPARATOR;
    public static final byte PARAGRAPH_SEPARATOR = java.lang.Character.PARAGRAPH_SEPARATOR;
    public static final byte CONTROL = java.lang.Character.CONTROL;
    public static final byte FORMAT = java.lang.Character.FORMAT;
    public static final byte PRIVATE_USE = java.lang.Character.PRIVATE_USE;
    public static final byte SURROGATE = java.lang.Character.SURROGATE;
    public static final byte DASH_PUNCTUATION = java.lang.Character.DASH_PUNCTUATION;
    public static final byte START_PUNCTUATION = java.lang.Character.START_PUNCTUATION;
    public static final byte END_PUNCTUATION = java.lang.Character.END_PUNCTUATION;
    public static final byte CONNECTOR_PUNCTUATION = java.lang.Character.CONNECTOR_PUNCTUATION;
    public static final byte OTHER_PUNCTUATION = java.lang.Character.OTHER_PUNCTUATION;
    public static final byte MATH_SYMBOL = java.lang.Character.MATH_SYMBOL;
    public static final byte CURRENCY_SYMBOL = java.lang.Character.CURRENCY_SYMBOL;
    public static final byte MODIFIER_SYMBOL = java.lang.Character.MODIFIER_SYMBOL;
    public static final byte OTHER_SYMBOL = java.lang.Character.OTHER_SYMBOL;
    public static final byte INITIAL_QUOTE_PUNCTUATION = java.lang.Character.INITIAL_QUOTE_PUNCTUATION;
    public static final byte FINAL_QUOTE_PUNCTUATION = java.lang.Character.FINAL_QUOTE_PUNCTUATION;
    public static final byte DIRECTIONALITY_UNDEFINED = java.lang.Character.DIRECTIONALITY_UNDEFINED;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT = java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT = java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC = java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER = java.lang.Character.DIRECTIONALITY_EUROPEAN_NUMBER;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR = java.lang.Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR;
    public static final byte DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR = java.lang.Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR;
    public static final byte DIRECTIONALITY_ARABIC_NUMBER = java.lang.Character.DIRECTIONALITY_ARABIC_NUMBER;
    public static final byte DIRECTIONALITY_COMMON_NUMBER_SEPARATOR = java.lang.Character.DIRECTIONALITY_COMMON_NUMBER_SEPARATOR;
    public static final byte DIRECTIONALITY_NONSPACING_MARK = java.lang.Character.DIRECTIONALITY_NONSPACING_MARK;
    public static final byte DIRECTIONALITY_BOUNDARY_NEUTRAL = java.lang.Character.DIRECTIONALITY_BOUNDARY_NEUTRAL;
    public static final byte DIRECTIONALITY_PARAGRAPH_SEPARATOR = java.lang.Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR;
    public static final byte DIRECTIONALITY_SEGMENT_SEPARATOR = java.lang.Character.DIRECTIONALITY_SEGMENT_SEPARATOR;
    public static final byte DIRECTIONALITY_WHITESPACE = java.lang.Character.DIRECTIONALITY_WHITESPACE;
    public static final byte DIRECTIONALITY_OTHER_NEUTRALS = java.lang.Character.DIRECTIONALITY_OTHER_NEUTRALS;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING = java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING;
    public static final byte DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE = java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING = java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING;
    public static final byte DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE = java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
    public static final byte DIRECTIONALITY_POP_DIRECTIONAL_FORMAT = java.lang.Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT;
    public static final char MIN_HIGH_SURROGATE = java.lang.Character.MIN_HIGH_SURROGATE;
    public static final char MAX_HIGH_SURROGATE = java.lang.Character.MAX_HIGH_SURROGATE;
    public static final char MIN_LOW_SURROGATE = java.lang.Character.MIN_LOW_SURROGATE;
    public static final char MAX_LOW_SURROGATE = java.lang.Character.MAX_LOW_SURROGATE;
    public static final char MIN_SURROGATE = java.lang.Character.MIN_SURROGATE;
    public static final char MAX_SURROGATE = java.lang.Character.MAX_SURROGATE;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT;
    public static final int MIN_CODE_POINT = java.lang.Character.MIN_CODE_POINT;
    public static final int MAX_CODE_POINT = java.lang.Character.MAX_CODE_POINT;
    public static final int BYTES = java.lang.Character.BYTES;
    public static final int SIZE = java.lang.Character.SIZE;

    private final char value;

    public Character(char c) {
        this.value = c;
    }

    public char charValue() {
        return this.value;
    }

    @Override
    public int hashCode() {
        return hashCode(this.value);
    }

    public static int hashCode(char value) {
        return java.lang.Character.hashCode(value);
    }

    @Override
    public boolean equals(java.lang.Object other) {
        return (other instanceof Character) && ((Character) other).value == value;
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return java.lang.Character.toString(value);
    }

    @Override
    @NotNull
    public String toDJVMString() {
        return toString(value);
    }

    @Override
    @NotNull
    java.lang.Character fromDJVM() {
        return value;
    }

    @Override
    public int compareTo(@NotNull Character var1) {
        return compare(this.value, var1.value);
    }

    public static int compare(char x, char y) {
        return java.lang.Character.compare(x, y);
    }

    public static String toString(char c) {
        return String.toDJVM(java.lang.Character.toString(c));
    }

    public static Character valueOf(char c) {
        return (c <= 127) ? Cache.cache[(int)c] : new Character(c);
    }

    public static boolean isValidCodePoint(int codePoint) {
        return java.lang.Character.isValidCodePoint(codePoint);
    }

    public static boolean isBmpCodePoint(int codePoint) {
        return java.lang.Character.isBmpCodePoint(codePoint);
    }

    public static boolean isSupplementaryCodePoint(int codePoint) {
        return java.lang.Character.isSupplementaryCodePoint(codePoint);
    }

    public static boolean isHighSurrogate(char ch) {
        return java.lang.Character.isHighSurrogate(ch);
    }

    public static boolean isLowSurrogate(char ch) {
        return java.lang.Character.isLowSurrogate(ch);
    }

    public static boolean isSurrogate(char ch) {
        return java.lang.Character.isSurrogate(ch);
    }

    public static boolean isSurrogatePair(char high, char low) {
        return java.lang.Character.isSurrogatePair(high, low);
    }

    public static int charCount(int codePoint) {
        return java.lang.Character.charCount(codePoint);
    }

    public static int toCodePoint(char high, char low) {
        return java.lang.Character.toCodePoint(high, low);
    }

    public static int codePointAt(CharSequence seq, int index) {
        return java.lang.Character.codePointAt(seq, index);
    }

    public static int codePointAt(char[] a, int index) {
        return java.lang.Character.codePointAt(a, index);
    }

    public static int codePointAt(char[] a, int index, int limit) {
        return java.lang.Character.codePointAt(a, index, limit);
    }

    public static int codePointBefore(CharSequence seq, int index) {
        return java.lang.Character.codePointBefore(seq, index);
    }

    public static int codePointBefore(char[] a, int index) {
        return java.lang.Character.codePointBefore(a, index);
    }

    public static int codePointBefore(char[] a, int index, int limit) {
        return java.lang.Character.codePointBefore(a, index, limit);
    }

    public static char highSurrogate(int codePoint) {
        return java.lang.Character.highSurrogate(codePoint);
    }

    public static char lowSurrogate(int codePoint) {
        return java.lang.Character.lowSurrogate(codePoint);
    }

    public static int toChars(int codePoint, char[] dst, int dstIndex) {
        return java.lang.Character.toChars(codePoint, dst, dstIndex);
    }

    public static char[] toChars(int codePoint) {
        return java.lang.Character.toChars(codePoint);
    }

    public static int codePointCount(CharSequence seq, int beginIndex, int endIndex) {
        return java.lang.Character.codePointCount(seq, beginIndex, endIndex);
    }

    public static int codePointCount(char[] a, int offset, int count) {
        return java.lang.Character.codePointCount(a, offset, count);
    }

    public static int offsetByCodePoints(CharSequence seq, int index, int codePointOffset) {
        return java.lang.Character.offsetByCodePoints(seq, index, codePointOffset);
    }

    public static int offsetByCodePoints(char[] a, int start, int count, int index, int codePointOffset) {
        return java.lang.Character.offsetByCodePoints(a, start, count, index, codePointOffset);
    }

    public static boolean isLowerCase(char ch) {
        return java.lang.Character.isLowerCase(ch);
    }

    public static boolean isLowerCase(int codePoint) {
        return java.lang.Character.isLowerCase(codePoint);
    }

    public static boolean isUpperCase(char ch) {
        return  java.lang.Character.isUpperCase(ch);
    }

    public static boolean isUpperCase(int codePoint) {
        return java.lang.Character.isUpperCase(codePoint);
    }

    public static boolean isTitleCase(char ch) {
        return java.lang.Character.isTitleCase(ch);
    }

    public static boolean isTitleCase(int codePoint) {
        return java.lang.Character.isTitleCase(codePoint);
    }

    public static boolean isDigit(char ch) {
        return java.lang.Character.isDigit(ch);
    }

    public static boolean isDigit(int codePoint) {
        return java.lang.Character.isDigit(codePoint);
    }

    public static boolean isDefined(char ch) {
        return java.lang.Character.isDefined(ch);
    }

    public static boolean isDefined(int codePoint) {
        return java.lang.Character.isDefined(codePoint);
    }

    public static boolean isLetter(char ch) {
        return java.lang.Character.isLetter(ch);
    }

    public static boolean isLetter(int codePoint) {
        return java.lang.Character.isLetter(codePoint);
    }

    public static boolean isLetterOrDigit(char ch) {
        return java.lang.Character.isLetterOrDigit(ch);
    }

    public static boolean isLetterOrDigit(int codePoint) {
        return java.lang.Character.isLetterOrDigit(codePoint);
    }

    @Deprecated
    public static boolean isJavaLetter(char ch) {
        return java.lang.Character.isJavaLetter(ch);
    }

    @Deprecated
    public static boolean isJavaLetterOrDigit(char ch) {
        return java.lang.Character.isJavaLetterOrDigit(ch);
    }

    public static boolean isAlphabetic(int codePoint) {
        return java.lang.Character.isAlphabetic(codePoint);
    }

    public static boolean isIdeographic(int codePoint) {
        return java.lang.Character.isIdeographic(codePoint);
    }

    public static boolean isJavaIdentifierStart(char ch) {
        return java.lang.Character.isJavaIdentifierStart(ch);
    }

    public static boolean isJavaIdentifierStart(int codePoint) {
        return java.lang.Character.isJavaIdentifierStart(codePoint);
    }

    public static boolean isJavaIdentifierPart(char ch) {
        return java.lang.Character.isJavaIdentifierPart(ch);
    }

    public static boolean isJavaIdentifierPart(int codePoint) {
        return java.lang.Character.isJavaIdentifierPart(codePoint);
    }

    public static boolean isUnicodeIdentifierStart(char ch) {
        return java.lang.Character.isUnicodeIdentifierStart(ch);
    }

    public static boolean isUnicodeIdentifierStart(int codePoint) {
        return java.lang.Character.isUnicodeIdentifierStart(codePoint);
    }

    public static boolean isUnicodeIdentifierPart(char ch) {
        return java.lang.Character.isUnicodeIdentifierPart(ch);
    }

    public static boolean isUnicodeIdentifierPart(int codePoint) {
        return java.lang.Character.isUnicodeIdentifierPart(codePoint);
    }

    public static boolean isIdentifierIgnorable(char ch) {
        return java.lang.Character.isIdentifierIgnorable(ch);
    }

    public static boolean isIdentifierIgnorable(int codePoint) {
        return java.lang.Character.isIdentifierIgnorable(codePoint);
    }

    public static char toLowerCase(char ch) {
        return java.lang.Character.toLowerCase(ch);
    }

    public static int toLowerCase(int codePoint) {
        return java.lang.Character.toLowerCase(codePoint);
    }

    public static char toUpperCase(char ch) {
        return java.lang.Character.toUpperCase(ch);
    }

    public static int toUpperCase(int codePoint) {
        return java.lang.Character.toUpperCase(codePoint);
    }

    public static char toTitleCase(char ch) {
        return java.lang.Character.toTitleCase(ch);
    }

    public static int toTitleCase(int codePoint) {
        return java.lang.Character.toTitleCase(codePoint);
    }

    public static int digit(char ch, int radix) {
        return java.lang.Character.digit(ch, radix);
    }

    public static int digit(int codePoint, int radix) {
        return java.lang.Character.digit(codePoint, radix);
    }

    public static int getNumericValue(char ch) {
        return java.lang.Character.getNumericValue(ch);
    }

    public static int getNumericValue(int codePoint) {
        return java.lang.Character.getNumericValue(codePoint);
    }

    @Deprecated
    public static boolean isSpace(char ch) {
        return java.lang.Character.isSpace(ch);
    }

    public static boolean isSpaceChar(char ch) {
        return java.lang.Character.isSpaceChar(ch);
    }

    public static boolean isSpaceChar(int codePoint) {
        return java.lang.Character.isSpaceChar(codePoint);
    }

    public static boolean isWhitespace(char ch) {
        return java.lang.Character.isWhitespace(ch);
    }

    public static boolean isWhitespace(int codePoint) {
        return java.lang.Character.isWhitespace(codePoint);
    }

    public static boolean isISOControl(char ch) {
        return java.lang.Character.isISOControl(ch);
    }

    public static boolean isISOControl(int codePoint) {
        return java.lang.Character.isISOControl(codePoint);
    }

    public static int getType(char ch) {
        return java.lang.Character.getType(ch);
    }

    public static int getType(int codePoint) {
        return java.lang.Character.getType(codePoint);
    }

    public static char forDigit(int digit, int radix) {
        return java.lang.Character.forDigit(digit, radix);
    }

    public static byte getDirectionality(char ch) {
        return java.lang.Character.getDirectionality(ch);
    }

    public static byte getDirectionality(int codePoint) {
        return java.lang.Character.getDirectionality(codePoint);
    }

    public static boolean isMirrored(char ch) {
        return java.lang.Character.isMirrored(ch);
    }

    public static boolean isMirrored(int codePoint) {
        return java.lang.Character.isMirrored(codePoint);
    }

    public static String getName(int codePoint) {
        return String.toDJVM(java.lang.Character.getName(codePoint));
    }

    public static Character toDJVM(java.lang.Character c) {
        return (c == null) ? null : valueOf(c);
    }

    // These three nested classes are placeholders to ensure that
    // the Character class bytecode is generated correctly. The
    // real classes will be loaded from the from the bootstrap jar
    // and then mapped into the sandbox.* namespace.
    public static final class UnicodeScript extends Enum<UnicodeScript> {
        private UnicodeScript(String name, int index) {
            super(name, index);
        }

        @Override
        public int compareTo(@NotNull UnicodeScript other) {
            throw new UnsupportedOperationException("Bootstrap implementation");
        }
    }
    public static final class UnicodeBlock extends Subset {}
    public static class Subset extends Object {}

    /**
     * Keep pre-allocated instances of the first 128 characters
     * on the basis that these will be used most frequently.
     */
    private static class Cache {
        private static final Character[] cache = new Character[128];

        static {
            for (int c = 0; c < cache.length; ++c) {
                cache[c] = new Character((char) c);
            }
        }

        private Cache() {}
    }
}
