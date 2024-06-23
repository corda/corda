package net.corda.testing.driver.junit.jupiter

/**
 * [@BeforeTestInCordaDriverContext] is used to annotate a method
 * which should be executed before each test but already in the context
 * of the Corda driver, meaning the Corda node handles/ rpc clients
 * are available.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BeforeEachTestInCordaDriverContext