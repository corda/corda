package com.stefano.testing;

import io.fabric8.kubernetes.api.model.Pod;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class KubePodResult {

    private final Pod createdPod;
    private final CompletableFuture<Void> waiter;
    private volatile Integer resultCode = 255;
    private final File output;

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
