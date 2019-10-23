package net.corda.testing.balancing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

interface PeekableIterator<T> extends Iterator<T> {

    static @NotNull <T> PeekableIterator<T> on(@NotNull Iterable<T> iterable) {
        return wrapping(iterable.iterator());
    }

    static  @NotNull <T> PeekableIterator<T> wrapping(@NotNull Iterator<T> iterator) {
        return new PeekableIterator<T>() {

            private @Nullable T buffered;

            @Override
            public boolean hasNext() {
                return buffered != null || iterator.hasNext();
            }

            @Override
            public @Nullable T next() {
                if (buffered == null) {
                    return iterator.next();
                }
                T unbuffered = buffered;
                buffered = null;
                return unbuffered;
            }

            @Override
            public @Nullable T peek() {
                if (buffered == null) {
                    buffered = iterator.next();
                }
                return buffered;
            }
        };
    }

    @Nullable T peek();
}
