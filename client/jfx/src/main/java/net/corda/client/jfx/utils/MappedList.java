package net.corda.client.jfx.utils;

import javafx.collections.ObservableList;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

// Java 9 introduces a new abstract method that we need to override (without using the explicit Kotlin `override` keyword to be backwards compatible
// https://docs.oracle.com/javase/9/docs/api/javafx/collections/transformation/TransformationList.html#getViewIndex-int-
public class MappedList<A,B> extends AbstractMappedList<A,B> {
    public MappedList(@NotNull ObservableList<A> list, @NotNull Function1<A,B> function) {
        super(list, function);
    }

    public int getViewIndex(int i) {
        return 0;
    }
}
