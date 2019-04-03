package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;
import java.io.Serializable;

@SuppressWarnings("unused")
public abstract class Number extends Object implements Serializable {

    public abstract double doubleValue();
    public abstract float floatValue();
    public abstract long longValue();
    public abstract int intValue();
    public abstract short shortValue();
    public abstract byte byteValue();

    @Override
    @NotNull
    public String toDJVMString() {
        return String.toDJVM(toString());
    }
}
