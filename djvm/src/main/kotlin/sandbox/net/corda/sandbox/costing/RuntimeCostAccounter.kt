package sandbox.net.corda.sandbox.costing

import net.corda.sandbox.SandboxRuntimeContext
import net.corda.sandbox.costing.RuntimeCostSummary
import net.corda.sandbox.costing.ThresholdViolationException

/**
 * Class for keeping a tally on various runtime metrics, like number of jumps, allocations, invocations, etc. The
 * functionality is implemented in [RuntimeCostSummary] but proxied through to this class so that the methods can be
 * accessed statically from within the sandbox.
 *
 * Note that the accounter also has thresholds for the various metrics that it is keeping track of, and that it will
 * terminate any sandboxed functions that breach these constraints.
 */
@Suppress("unused")
object RuntimeCostAccounter {

    /**
     * A static instance of the sandbox context which is used to keep track of the costs.
     */
    private val context: SandboxRuntimeContext
        get() = SandboxRuntimeContext.INSTANCE

    /**
     * The type name of the [RuntimeCostAccounter] class; referenced from instrumentors.
     */
    const val TYPE_NAME: String = "sandbox/net/corda/sandbox/costing/RuntimeCostAccounter"

    /**
     * Known / estimated allocation costs.
     */
    private val allocationCosts = mapOf(
            "java/lang/Object" to 8,
            "sandbox/java/lang/Object" to 8
    )

    /**
     * Re-throw exception if it is of type [ThreadDeath] or [ThresholdViolationException].
     */
    @JvmStatic
    fun checkCatch(exception: Throwable) {
        if (exception is ThreadDeath) {
            throw exception
        } else if (exception is ThresholdViolationException) {
            throw exception
        }
    }

    /**
     * Record a jump operation.
     */
    @JvmStatic
    fun recordJump() =
            context.runtimeCosts.jumpCost.increment()

    /**
     * Record a memory allocation operation.
     *
     * @param typeName The class name of the object being instantiated.
     */
    @JvmStatic
    fun recordAllocation(typeName: String) {
        // TODO Derive better size estimates for complex types.
        val size = allocationCosts.getOrDefault(typeName, 16)
        context.runtimeCosts.allocationCost.increment(size)
    }

    /**
     * Record an array allocation operation.
     *
     * @param length The number of elements in the array.
     * @param typeName The class name of the array element type.
     */
    @JvmStatic
    fun recordArrayAllocation(length: Int, typeName: String) {
        val size = allocationCosts.getOrDefault(typeName, 16)
        context.runtimeCosts.allocationCost.increment(length * size)
    }

    /**
     * Record an array allocation operation.
     *
     * @param length The number of elements in the array.
     * @param typeSize The size of the array element type.
     */
    @JvmStatic
    fun recordArrayAllocation(length: Int, typeSize: Int) {
        context.runtimeCosts.allocationCost.increment(length * typeSize)
    }

    /**
     * Record a method call.
     */
    @JvmStatic
    fun recordInvocation() =
            context.runtimeCosts.invocationCost.increment()

    /**
     * The accumulated cost of exception throws that have been made.
     */
    @JvmStatic
    fun recordThrow() =
            context.runtimeCosts.throwCost.increment()

}
