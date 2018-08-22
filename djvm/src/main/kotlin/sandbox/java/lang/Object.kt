package sandbox.java.lang

/**
 * Sandboxed implementation of `java/lang/Object`.
 */
@Suppress("EqualsOrHashCode")
open class Object {

    /**
     * Deterministic hash code for objects.
     */
    override fun hashCode(): Int = sandbox.java.lang.System.identityHashCode(this)

    /**
     * Deterministic string representation of [Object].
     */
    override fun toString(): String = "sandbox.java.lang.Object@${hashCode().toString(16)}"

}
