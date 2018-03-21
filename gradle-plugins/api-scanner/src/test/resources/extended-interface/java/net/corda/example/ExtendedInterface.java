package net.corda.example;

import java.util.concurrent.Future;

public interface ExtendedInterface<T> extends Future<T> {
    String getName();
    void setName(String name);
}
