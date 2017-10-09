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
