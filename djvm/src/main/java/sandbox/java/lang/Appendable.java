package sandbox.java.lang;

import java.io.IOException;

/**
 * This is a dummy class that implements just enough of [java.lang.Appendable]
 * to keep [sandbox.java.lang.StringBuilder], [sandbox.java.lang.StringBuffer]
 * and [sandbox.java.lang.String] honest.
 * Note that it does not extend [java.lang.Appendable].
 */
public interface Appendable {

    Appendable append(CharSequence csq, int start, int end) throws IOException;

    Appendable append(CharSequence csq) throws IOException;

    Appendable append(char c) throws IOException;

}
