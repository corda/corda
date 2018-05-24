package sandbox.java.lang

/**
 * Sandboxed implementation of `java/lang/Object`.
 */
@Suppress("EqualsOrHashCode")
open class Object(private val hashValue: Int) {

    /**
     * Deterministic hash code for objects.
     */
    override fun hashCode(): Int = hashValue

    /**
     * Deterministic string representation of [Object].
     */
    override fun toString(): String = "sandbox.java.lang.Object@${hashCode().toString(16)}"

}
