package net.corda.sandbox.references

/**
 * Annotation-specific functionality.
 */
open class AnnotationModule {

    /**
     * Check if any of the annotations marks determinism.
     */
    fun isDeterministic(annotations: Set<String>): Boolean {
        return annotations.any { isDeterministic(it) }
    }

    /**
     * Check if annotation is deterministic.
     */
    fun isDeterministic(annotation: String): Boolean {
        return annotation.endsWith("/deterministic;", true)
    }

    /**
     * Check if any of the annotations marks non-determinism.
     */
    fun isNonDeterministic(annotations: Set<String>): Boolean {
        return annotations.any { isNonDeterministic(it) }
    }

    /**
     * Check if annotation in non-deterministic.
     */
    fun isNonDeterministic(annotation: String): Boolean {
        return annotation.endsWith("/nondeterministic;", true)
    }

}