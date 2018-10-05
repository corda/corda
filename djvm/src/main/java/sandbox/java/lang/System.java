package sandbox.java.lang;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class System extends Object {

    private System() {}

    /*
     * This class is duplicated into every sandbox, where everything is single-threaded.
     */
    private static final java.util.Map<java.lang.Integer, java.lang.Integer> objectHashCodes = new java.util.LinkedHashMap<>();
    private static int objectCounter = 0;

    public static int identityHashCode(java.lang.Object obj) {
        int nativeHashCode = java.lang.System.identityHashCode(obj);
        // TODO Instead of using a magic offset below, one could take in a per-context seed
        return objectHashCodes.computeIfAbsent(nativeHashCode, i -> ++objectCounter + 0xfed_c0de);
    }

    public static final String lineSeparator = String.toDJVM("\n");

    public static void arraycopy(java.lang.Object src, int srcPos, java.lang.Object dest, int destPos, int length) {
        java.lang.System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static void runFinalization() {}
    public static void gc() {}
}
