package net.corda.serialization.djvm.serializers;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * This class is deliberately written in Java so
 * that it can be package private.
 */
final class CacheKey {
    private final byte[] bytes;
    private final int hashValue;

    CacheKey(@NotNull byte[] bytes) {
        this.bytes = bytes;
        this.hashValue = Arrays.hashCode(bytes);
    }

    @NotNull
    byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        return (this == other)
            || (other instanceof CacheKey && Arrays.equals(bytes, ((CacheKey) other).bytes));
    }

    @Override
    public int hashCode() {
        return hashValue;
    }
}
