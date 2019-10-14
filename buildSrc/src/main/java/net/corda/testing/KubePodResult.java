package net.corda.testing;

import io.fabric8.kubernetes.api.model.Pod;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class KubePodResult {

    private final Pod createdPod;
    private volatile Integer resultCode = 255;
    private final File output;
    private volatile Collection<File> binaryResults = Collections.emptyList();

    KubePodResult(Pod createdPod, File output) {
        this.createdPod = createdPod;
        this.output = output;
    }

    public void setResultCode(Integer code) {
        synchronized (createdPod) {
            this.resultCode = code;
        }
    }

    public Integer getResultCode() {
        synchronized (createdPod) {
            return this.resultCode;
        }
    }

    public File getOutput() {
        return output;
    }

    public Pod getCreatedPod() {
        return createdPod;
    }

    public Collection<File> getBinaryResults() {
        synchronized (createdPod) {
            return binaryResults;
        }
    }

    public void setBinaryResults(Collection<File> binaryResults) {
        synchronized (createdPod) {
            this.binaryResults = binaryResults;
        }
    }
};
