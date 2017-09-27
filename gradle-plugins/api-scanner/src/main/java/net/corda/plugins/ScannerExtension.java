package net.corda.plugins;

@SuppressWarnings("unused")
public class ScannerExtension {

    private boolean verbose;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
