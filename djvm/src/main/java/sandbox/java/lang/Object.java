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

    public static java.lang.Object[] fromDJVM(java.lang.Object[] args) {
        if (args == null) {
            return null;
        }

        java.lang.Object[] unwrapped = (java.lang.Object[]) java.lang.reflect.Array.newInstance(
            fromDJVM(args.getClass().getComponentType()), args.length
        );
        int i = 0;
        for (java.lang.Object arg : args) {
            unwrapped[i] = unwrap(arg);
            ++i;
        }
        return unwrapped;
    }

    private static java.lang.Object unwrap(java.lang.Object arg) {
        if (arg instanceof Object) {
            return ((Object) arg).fromDJVM();
        } else if (Object[].class.isAssignableFrom(arg.getClass())) {
            return fromDJVM((Object[]) arg);
        } else {
            return arg;
        }
    }

    private static Class<?> fromDJVM(Class<?> type) {
        if (type == String.class) {
            return java.lang.String.class;
        } else if (type == Integer.class) {
            return java.lang.Integer.class;
        } else if (type == Long.class) {
            return java.lang.Long.class;
        } else if (type == Short.class) {
            return java.lang.Short.class;
        } else if (type == Byte.class) {
            return java.lang.Byte.class;
        } else if (type == Double.class) {
            return java.lang.Double.class;
        } else if (type == Float.class) {
            return java.lang.Float.class;
        } else if (type == Character.class) {
            return java.lang.Character.class;
        } else if (type == Boolean.class) {
            return java.lang.Boolean.class;
        } else if (type == Object.class) {
            return java.lang.Object.class;
        } else {
            return type;
        }
    }

    static java.util.Locale fromDJVM(sandbox.java.util.Locale locale) {
        return java.util.Locale.forLanguageTag(locale.toLanguageTag().fromDJVM());
    }

    static java.nio.charset.Charset fromDJVM(sandbox.java.nio.charset.Charset charset) {
        return java.nio.charset.Charset.forName(charset.name().fromDJVM());
    }
}
