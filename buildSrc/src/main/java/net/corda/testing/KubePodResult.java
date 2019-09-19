package net.corda.testing;

import io.fabric8.kubernetes.api.model.Pod;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class KubePodResult {

    private final Pod createdPod;
    private final CompletableFuture<Void> waiter;
    private volatile Integer resultCode = 255;
    private final File output;
    private volatile Collection<File> binaryResults = Collections.emptyList();

    KubePodResult(Pod createdPod, CompletableFuture<Void> waiter, File output) {
        this.createdPod = createdPod;
        this.waiter = waiter;
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
        synchronized (createdPod) {
            return output;
        }
    }
};
