package com.stefano.testing;

import io.fabric8.kubernetes.api.model.Pod;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

;

public class KubePodResult {

    private final Pod createdPod;
    ;
    private final AtomicReference<Throwable> errorHolder;
    private final CompletableFuture<Void> waiter;
    private volatile Integer resultCode = 255;
    private final File output;

    KubePodResult(Pod createdPod, AtomicReference<Throwable> errorHolder, CompletableFuture<Void> waiter, File output) {
        ;
        this.createdPod = createdPod;
        this.errorHolder = errorHolder;
        this.waiter = waiter;
        this.output = output;
    }

    public void setResultCode(Integer code) {
        synchronized (errorHolder) {
            this.resultCode = code;
        }
    }

    public Integer getResultCode() {
        synchronized (errorHolder) {
            return this.resultCode;
        }
    }

    public File getOutput() {
        synchronized (errorHolder) {
            return output;
        }
    }
};
