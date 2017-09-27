package net.corda.plugins;

@SuppressWarnings("unused")
public class ScannerExtension {

    private boolean verbose;
    private boolean enabled = true;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
