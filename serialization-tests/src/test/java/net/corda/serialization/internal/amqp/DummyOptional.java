package net.corda.serialization.internal.amqp;

import net.corda.core.serialization.CordaSerializable;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DummyOptional<?> that = (DummyOptional<?>) o;
        return Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {

        return Objects.hash(item);
    }
}