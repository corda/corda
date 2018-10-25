package sandbox.java.lang;

import java.io.Serializable;

/**
 * This is a dummy class that implements just enough of {@link java.lang.StringBuilder}
 * to allow us to compile {@link sandbox.java.lang.String}.
 */
public abstract class StringBuilder extends Object implements Appendable, CharSequence, Serializable {

    @Override
    public abstract StringBuilder append(CharSequence seq);

    @Override
    public abstract StringBuilder append(CharSequence seq, int start, int end);

    @Override
    public abstract StringBuilder append(char c);

}
