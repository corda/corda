/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package sandbox.net.corda.sandbox.costing;

/**
 * A helper class that just forwards any static sandboxed calls to the real runtime
 * cost accounting class. This removes the need to special case the accounting
 * method calls during rewriting of method names
 *
 * @author ben
 */
public class RuntimeCostAccounter {

    public static void recordJump() {
        net.corda.sandbox.costing.RuntimeCostAccounter.recordJump();
    }

    public static void recordAllocation(final String typeName) {
        net.corda.sandbox.costing.RuntimeCostAccounter.recordAllocation(typeName);
    }

    public static void recordArrayAllocation(final int length, final int multiplier) {
        net.corda.sandbox.costing.RuntimeCostAccounter.recordArrayAllocation(length, multiplier);
    }

    public static void recordMethodCall() {
        net.corda.sandbox.costing.RuntimeCostAccounter.recordMethodCall();
    }

    public static void recordThrow() {
        net.corda.sandbox.costing.RuntimeCostAccounter.recordThrow();
    }

}
