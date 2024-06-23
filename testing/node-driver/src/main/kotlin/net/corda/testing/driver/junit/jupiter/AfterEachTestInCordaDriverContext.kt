package net.corda.testing.driver.junit.jupiter

/**
 * [@AfterTestInCordaDriverContext] is used to annotate a method
 * which should be executed after each test but still within the context
 * of the Corda driver, meaning the Corda node handles/ rpc clients
 * are available.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AfterEachTestInCordaDriverContext