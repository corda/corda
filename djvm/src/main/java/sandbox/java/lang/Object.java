package sandbox.java.lang;

import org.jetbrains.annotations.NotNull;

public class Object {

    @Override
    public int hashCode() {
        return sandbox.java.lang.System.identityHashCode(this);
    }

    @Override
    @NotNull
    public java.lang.String toString() {
        return toDJVMString().toString();
    }

    @NotNull
    public String toDJVMString() {
        return String.toDJVM("sandbox.java.lang.Object@" + java.lang.Integer.toString(hashCode(), 16));
    }

    @NotNull
    java.lang.Object fromDJVM() {
        return this;
    }

    public static java.lang.Object[] fromDJVM(Object[] args) {
        if (args == null) {
            return null;
        }

        java.lang.Object[] unwrapped = new java.lang.Object[args.length];
        int i = 0;
        for (Object arg: args) {
            unwrapped[i] = arg.fromDJVM();
            ++i;
        }
        return unwrapped;
    }

    static java.util.Locale fromDJVM(sandbox.java.util.Locale locale) {
        return java.util.Locale.forLanguageTag(locale.toLanguageTag().fromDJVM());
    }

    static java.nio.charset.Charset fromDJVM(sandbox.java.nio.charset.Charset charset) {
        return java.nio.charset.Charset.forName(charset.name().fromDJVM());
    }
}
