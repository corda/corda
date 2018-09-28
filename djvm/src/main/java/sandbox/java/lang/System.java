package sandbox.java.lang;

public final class System extends Object {

    private static final ThreadLocal<java.lang.Integer> objectCounter = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<java.util.Map<java.lang.Integer, java.lang.Integer>> objectHashCodes = ThreadLocal.withInitial(java.util.HashMap::new);

    public static int identityHashCode(Object obj) {
        int nativeHashCode = java.lang.System.identityHashCode(obj);
        // TODO Instead of using a magic offset below, one could take in a per-context seed
        java.lang.Integer hash = objectHashCodes.get().get(nativeHashCode);
        if (hash == null) {
            int newCounter = objectCounter.get() + 1;
            objectCounter.set(newCounter);
            hash = 0xfed_c0de + newCounter;
        }
        return hash;
    }

}
