package sandbox.java.util.function;

/**
 * This is a dummy class that implements just enough of @{link java.util.function.Supplier}
 * to allow us to compile {@link sandbox.java.lang.ThreadLocal}.
 */
@FunctionalInterface
public interface Supplier<T> {
    T get();
}
