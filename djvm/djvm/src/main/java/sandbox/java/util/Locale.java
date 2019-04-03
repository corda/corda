package sandbox.java.util;

/**
 * This is a dummy class that implements just enough of {@link java.util.Locale}
 * to allow us to compile {@link sandbox.java.lang.String}.
 */
public abstract class Locale extends sandbox.java.lang.Object {
    public abstract sandbox.java.lang.String toLanguageTag();
}
