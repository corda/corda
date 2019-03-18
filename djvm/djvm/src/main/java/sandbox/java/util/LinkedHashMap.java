package sandbox.java.util;

/**
 * This is a dummy class to bootstrap us into the sandbox.
 */
public class LinkedHashMap<K, V> extends java.util.LinkedHashMap<K, V> implements Map<K, V> {
    public LinkedHashMap(int initialSize) {
        super(initialSize);
    }

    public LinkedHashMap() {
    }
}
