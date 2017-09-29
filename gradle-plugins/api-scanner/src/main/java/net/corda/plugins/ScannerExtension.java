package net.corda.plugins;

import java.util.List;

import static java.util.Collections.emptyList;

@SuppressWarnings("unused")
public class ScannerExtension {

    private boolean verbose;
    private boolean enabled = true;
    private List<String> excludeClasses = emptyList();

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

    public List<String> getExcludeClasses() {
        return excludeClasses;
    }

    public void setExcludeClasses(List<String> excludeClasses) {
        this.excludeClasses = excludeClasses;
    }
}
