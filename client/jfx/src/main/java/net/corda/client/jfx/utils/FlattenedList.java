package net.corda.client.jfx.utils;

import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

// Java 9 introduces a new abstract method that we need to override (without using the explicit Kotlin `override` keyword to be backwards compatible
// https://docs.oracle.com/javase/9/docs/api/javafx/collections/transformation/TransformationList.html#getViewIndex-int-
public class FlattenedList extends AbstractFlattenedList {
    @SuppressWarnings("unchecked")
    public FlattenedList(@NotNull ObservableList sourceList) {
        super(sourceList);
    }

    public int getViewIndex(int i) {
        return 0;
    }
}
