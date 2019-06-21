package net.corda.client.jfx.utils;

import javafx.collections.ObservableList;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;

// Java 9 introduces a new abstract method that we need to override (without using the explicit Kotlin `override` keyword to be backwards compatible
// https://docs.oracle.com/javase/9/docs/api/javafx/collections/transformation/TransformationList.html#getViewIndex-int-
public class AggregatedList<A,E,K> extends AbstractAggregatedList<A,E,K> {
    @SuppressWarnings("unchecked")
    public AggregatedList(@NotNull ObservableList<? extends E> list, @NotNull Function1<E,K> toKey, @NotNull Function2<K,ObservableList<E>,A> assemble) {
        super(list, toKey, assemble);
    }

    public int getViewIndex(int i) {
        return 0;
    }
}
