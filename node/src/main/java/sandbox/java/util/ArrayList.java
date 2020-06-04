package sandbox.java.util;

/**
 * This is a dummy class that implements just enough of {@link java.util.ArrayList}
 * to allow us to compile {@link sandbox.net.corda.core.crypto.Crypto}.
 */
@SuppressWarnings("unused")
public class ArrayList<T> extends sandbox.java.lang.Object implements List<T> {
    public ArrayList(int size) {
    }

    @Override
    public boolean add(T item) {
        throw new UnsupportedOperationException("Dummy class - not implemented");
    }
}
