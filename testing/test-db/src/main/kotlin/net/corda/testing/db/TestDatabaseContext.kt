package net.corda.testing.db

import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Interface which must be implemented by any class offering to manage test database environments for tests annotated with [RequiresDb].
 *
 * A separate instance of [TestDatabaseContext] will be created and initialised for each group of tests, identified by [RequiresDb.group].
 *
 * Once all tests in the group have been run, [ExtensionContext.Store.CloseableResource.close] will be called; implementations should use
 * this method to tear down the database context.
 */
interface TestDatabaseContext : ExtensionContext.Store.CloseableResource {

    /**
     * Called once when the context is first instantiated, i.e. at the start of the test run, before any tests at all have been executed.
     *
     * @param groupName The name of the group of tests whose database environment is to be managed by this context.
     */
    fun initialize(groupName: String)

    /**
     * Called once if some setup SQL needs to be run before a suite of tests is executed, as indicated by a [RequiresSql] annotation on the
     * class containing the test suite.
     *
     * @param setupSql The name of the SQL script to be run prior to running the suite of tests.
     */
    fun beforeClass(setupSql: String)

    /**
     * Called once if some setup SQL needs to be run after a suite of tests is executed, as indicated by a [RequiresSql] annotation on the
     * class containing the test suite.
     *
     * @param teardownSql The name of the SQL script to be run after running the suite of tests.
     */
    fun afterClass(teardownSql: String)

    /**
     * Called once if some setup SQL needs to be run before a given test is executed, as indicated by a [RequiresSql] annotation on the
     * method defining the test
     *
     * @param setUpSql The name of the SQL script to be run before running the test.
     */
    fun beforeTest(setupSql: String)

    /**
     * Called once if some setup SQL needs to be run after a given test is executed, as indicated by a [RequiresSql] annotation on the
     * method defining the test
     *
     * @param teardownSql The name of the SQL script to be run after running the test.
     */
    fun afterTest(teardownSql: String)
}