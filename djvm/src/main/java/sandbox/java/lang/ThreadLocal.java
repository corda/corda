package sandbox.java.lang;

import sandbox.java.util.function.Supplier;

/**
 * Everything inside the sandbox is single-threaded, so this
 * implementation of ThreadLocal is sufficient.
 * @param <T> Underlying type of this thread-local variable.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class ThreadLocal<T> extends Object {

    private T value;
    private boolean isSet;

    public ThreadLocal() {
    }

    protected T initialValue() {
        return null;
    }

    public T get() {
        if (!isSet) {
            set(initialValue());
        }
        return value;
    }

    public void set(T value) {
        this.value = value;
        this.isSet = true;
    }

    public void remove() {
        value = null;
        isSet = false;
    }

    public static <V> ThreadLocal<V> withInitial(Supplier<? extends V> supplier) {
        return new SuppliedThreadLocal<>(supplier);
    }

    // Stub class for compiling ThreadLocal. The sandbox will import the
    // actual SuppliedThreadLocal class at run-time. Having said that, we
    // still need a working implementation here for the sake of our tests.
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {
        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = supplier;
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }
}
