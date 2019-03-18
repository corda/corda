package sandbox.java.lang;

import net.corda.djvm.SandboxRuntimeContext;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class System extends Object {

    private System() {}

    public static int identityHashCode(java.lang.Object obj) {
        int nativeHashCode = java.lang.System.identityHashCode(obj);
        return SandboxRuntimeContext.getInstance().getHashCodeFor(nativeHashCode);
    }

    public static final String lineSeparator = String.toDJVM("\n");

    public static void arraycopy(java.lang.Object src, int srcPos, java.lang.Object dest, int destPos, int length) {
        java.lang.System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static void runFinalization() {}
    public static void gc() {}
}
