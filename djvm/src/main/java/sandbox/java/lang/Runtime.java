package sandbox.java.lang;

@SuppressWarnings("unused")
public final class Runtime extends Object {
    private static final Runtime RUNTIME = new Runtime();

    private Runtime() {}

    public static Runtime getRuntime() {
        return RUNTIME;
    }

    /**
     * Everything inside the sandbox is single-threaded.
     * @return 1
     */
    public int availableProcessors() {
        return 1;
    }

    public void loadLibrary(String libraryName) {}

    public void load(String fileName) {}

    public void runFinalization() {}
    public void gc() {}
}
