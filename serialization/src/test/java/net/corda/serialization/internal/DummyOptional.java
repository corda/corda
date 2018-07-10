package net.corda.serialization.internal;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class DummyOptional<T> {

    private final T item;

    public boolean isPresent() {
        return item != null;
    }

    public T get() {
        return item;
    }

    public DummyOptional(T item) {
        this.item = item;
    }
}
