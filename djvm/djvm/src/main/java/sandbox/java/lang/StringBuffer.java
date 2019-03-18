package sandbox.java.lang;

import java.io.Serializable;

/**
 * This is a dummy class that implements just enough of {@link java.lang.StringBuffer}
 * to allow us to compile {@link sandbox.java.lang.String}.
 */
public abstract class StringBuffer extends Object implements CharSequence, Appendable, Serializable {

    @Override
    public abstract StringBuffer append(CharSequence seq);

    @Override
    public abstract StringBuffer append(CharSequence seq, int start, int end);

    @Override
    public abstract StringBuffer append(char c);

}
