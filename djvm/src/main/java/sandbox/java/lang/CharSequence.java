package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

/**
 * This is a dummy class that implements just enough of [java.lang.CharSequence]
 * to allow us to compile [sandbox.java.lang.String].
 */
public interface CharSequence extends java.lang.CharSequence {

    @Override
    CharSequence subSequence(int start, int end);

    @NotNull
    String toDJVMString();

}
