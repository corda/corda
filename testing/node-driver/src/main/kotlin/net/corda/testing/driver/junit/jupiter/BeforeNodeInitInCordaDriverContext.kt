package net.corda.testing.driver.junit.jupiter

/**
 * [@BeforeTestInCordaDriverContext] is used to annotate a method
 * which should be executed for each test within the context of the Corda Driver,
 * but before the Corda nodes are initialized.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BeforeNodeInitInCordaDriverContext