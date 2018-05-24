package net.corda.sandbox.annotations

/**
 * Annotation for marking a class, field or method as non-deterministic.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
        AnnotationTarget.FILE,
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.FIELD,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER
)
annotation class NonDeterministic