package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * This is a dummy class that implements just enough of {@link java.lang.Iterable}
 * to allow us to compile {@link sandbox.java.lang.String}.
 */
public interface Iterable<T> extends java.lang.Iterable<T> {
    @Override
    @NotNull
    Iterator<T> iterator();
}
