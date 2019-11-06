package net.corda.testing;

import java.io.File;
import java.util.Collection;

public class KubePodResult {

    private final int podIndex;
    private final int resultCode;
    private final File output;
    private final Collection<File> binaryResults;

    public KubePodResult(int podIndex, int resultCode, File output, Collection<File> binaryResults) {
        this.podIndex = podIndex;
        this.resultCode = resultCode;
        this.output = output;
        this.binaryResults = binaryResults;
    }

    public int getResultCode() {
        return resultCode;
    }

    public File getOutput() {
        return output;
    }

    public Collection<File> getBinaryResults() {
        return binaryResults;
    }

    public int getPodIndex() {
        return podIndex;
    }
}
