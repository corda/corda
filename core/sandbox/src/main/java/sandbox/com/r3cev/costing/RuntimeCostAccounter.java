package sandbox.com.r3cev.costing;

/**
 * A helper class that just forwards any static sandboxed calls to the real runtime
 * cost accounting class. This removes the need to special case the accounting
 * method calls during rewriting of method names
 * 
 * @author ben
 */
public class RuntimeCostAccounter {

    public static void recordJump() {
        com.r3cev.costing.RuntimeCostAccounter.recordJump();
    }

    public static void recordAllocation(final String typeName) {
        com.r3cev.costing.RuntimeCostAccounter.recordAllocation(typeName);
    }

    public static void recordArrayAllocation(final int length, final int multiplier) {
        com.r3cev.costing.RuntimeCostAccounter.recordArrayAllocation(length, multiplier);
    }

    public static void recordMethodCall() {
        com.r3cev.costing.RuntimeCostAccounter.recordMethodCall();
    }

    public static void recordThrow() {
        com.r3cev.costing.RuntimeCostAccounter.recordThrow();
    }

}
