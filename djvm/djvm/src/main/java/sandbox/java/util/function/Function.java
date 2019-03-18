package sandbox.java.util.function;

/**
 * This is a dummy class that implements just enough of {@link java.util.function.Function}
 * to allow us to compile {@link sandbox.Task}.
 */
@FunctionalInterface
public interface Function<T, R> {
    R apply(T item);
}
