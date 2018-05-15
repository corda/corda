/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.costing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ben
 */
public class RuntimeCostAccounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeCostAccounter.class);

    private static Thread primaryThread;

    private static final ThreadLocal<Long> allocationCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> jumpCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> invokeCost = ThreadLocal.withInitial(() -> 0L);

    private static final ThreadLocal<Long> throwCost = ThreadLocal.withInitial(() -> 0L);

    private static final long BASELINE_ALLOC_KILL_THRESHOLD = 1024 * 1024;

    private static final long BASELINE_JUMP_KILL_THRESHOLD = 100;

    private static final long BASELINE_INVOKE_KILL_THRESHOLD = 100;

    private static final long BASELINE_THROW_KILL_THRESHOLD = 50;

    public static void recordJump() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("In recordJump() at " + System.currentTimeMillis() + " on " + current.getName());
        checkJumpCost(1);
    }

    public static void recordAllocation(final String typeName) {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("In recordAllocation() at " + System.currentTimeMillis()
                    + ", got object type: " + typeName + " on " + current.getName());

        // More sophistication is clearly possible, e.g. caching approximate sizes for types that we encounter
        checkAllocationCost(1);
    }

    public static void recordArrayAllocation(final int length, final int multiplier) {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("In recordArrayAllocation() at " + System.currentTimeMillis()
                    + ", got array element size: " + multiplier + " and size: " + length + " on " + current.getName());

        checkAllocationCost(length * multiplier);
    }

    public static void recordMethodCall() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("In recordMethodCall() at " + System.currentTimeMillis() + " on " + current.getName());

        checkInvokeCost(1);
    }

    public static void recordThrow() {
        final Thread current = Thread.currentThread();
        if (current == primaryThread)
            return;

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("In recordThrow() at " + System.currentTimeMillis() + " on " + current.getName());
        checkThrowCost(1);
    }

    public static void setPrimaryThread(final Thread toBeIgnored) {
        primaryThread = toBeIgnored;
    }

    private static void checkAllocationCost(final long additional) {
        final long newValue = additional + allocationCost.get();
        allocationCost.set(newValue);
        if (newValue > BASELINE_ALLOC_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Contract " + current + " terminated for overallocation");
            throw new ThreadDeath();
        }
    }

    private static void checkJumpCost(final long additional) {
        final long newValue = additional + jumpCost.get();
        jumpCost.set(newValue);
        if (newValue > BASELINE_JUMP_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Contract " + current + " terminated for excessive use of looping");
            throw new ThreadDeath();
        }
    }

    private static void checkInvokeCost(final long additional) {
        final long newValue = additional + invokeCost.get();
        invokeCost.set(newValue);
        if (newValue > BASELINE_INVOKE_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Contract " + current + " terminated for excessive method calling");
            throw new ThreadDeath();
        }
    }

    private static void checkThrowCost(final long additional) {
        final long newValue = additional + throwCost.get();
        throwCost.set(newValue);
        if (newValue > BASELINE_THROW_KILL_THRESHOLD) {
            final Thread current = Thread.currentThread();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Contract " + current + " terminated for excessive exception throwing");
            throw new ThreadDeath();
        }
    }

    public static long getAllocationCost() {
        return allocationCost.get();
    }

    public static long getJumpCost() {
        return jumpCost.get();
    }

    public static long getInvokeCost() {
        return invokeCost.get();
    }

    public static long getThrowCost() {
        return throwCost.get();
    }

    public static void resetCounters() {
        allocationCost.set(0L);
        jumpCost.set(0L);
        invokeCost.set(0L);
        throwCost.set(0L);
    }
}
