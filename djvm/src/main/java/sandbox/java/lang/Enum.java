package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * This is a dummy class. We will load the actual Enum class at run-time.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class Enum<E extends Enum<E>> extends Object implements Comparable<E>, Serializable {

    private final String name;
    private final int ordinal;

    protected Enum(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        return ordinal;
    }

    @Override
    @NotNull
    final java.lang.Enum<?> fromDJVM() {
        throw new UnsupportedOperationException("Dummy implementation");
    }

}
