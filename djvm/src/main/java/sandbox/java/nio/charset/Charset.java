package sandbox.java.nio.charset;

/**
 * This is a dummy class that implements just enough of [java.nio.charset.Charset]
 * to allow us to compile [sandbox.java.lang.String].
 */
public abstract class Charset {
    private final sandbox.java.lang.String canonicalName;

    protected Charset(sandbox.java.lang.String canonicalName, sandbox.java.lang.String[] aliases) {
        this.canonicalName = canonicalName;
    }

    public final sandbox.java.lang.String name() {
        return canonicalName;
    }
}
