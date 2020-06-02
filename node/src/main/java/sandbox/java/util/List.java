package sandbox.java.util;

/**
 * This is a dummy class that implements just enough of {@link java.util.List}
 * to allow us to compile {@link sandbox.net.corda.core.crypto.Crypto}.
 */
public interface List<T> {
    boolean add(T item);
}
