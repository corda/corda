package net.corda.testing.internal.db

import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Interface which must be implemented by any class offering to manage test database environments for tests annotated with [RequiresDb],
 * or with annotations which are themselves annotated with [@RequiresDb].
 *
 * A separate instance of [TestDatabaseContext] will be created and initialised for each group of tests, identified by [RequiresDb.group].
 *
 * Once all tests in the group have been run, [ExtensionContext.Store.CloseableResource.close] will be called; implementations should use
 * this method to tear down the database context.
 */
interface TestDatabaseContext : ExtensionContext.Store.CloseableResource {

    companion object {
        private val _usingRemoteDatabase = ThreadLocal<Boolean>()

        /**
         * A flag that an instantiating class can set to indicate to tests that a remote database is in use.
         */
        var usingRemoteDatabase: Boolean
            get() = _usingRemoteDatabase.get() ?: false
            set(value) = _usingRemoteDatabase.set(value)
    }
    
    /**
     * Called once when the context is first instantiated, i.e. at the start of the test run, before any tests at all have been executed.
     *
     * @param groupName The name of the group of tests whose database environment is to be managed by this context, as indicated by a
     * [RequiresDb] annotation (or annotations which are themselves annotated with [@RequiresDb]) on each test class in this group.
     */
    fun initialize(groupName: String)

    /**
     * Called once before a suite of tests is executed.
     *
     * @param setupSql The names of any SQL scripts to be run prior to running the suite of tests, as indicated by a [RequiresSql] annotation
     * (or annotations which are themselves annotated with [RequiresSql]), on the class containing the test suite. May be empty.
     */
    fun beforeClass(setupSql: List<String>)

    /**
     * Called once after a suite of tests is executed.
     *
     * @param teardownSql The names of any SQL scripts to be run after running the suite of tests, as indicated by a [RequiresSql] annotation
     * (or annotations which are themselves annotated with [RequiresSql]), on the class containing the test suite. May be empty.
     */
    fun afterClass(teardownSql: List<String>)

    /**
     * Called once before a given test is executed.
     *
     * @param setUpSql The names of any SQL scripts to be run before running the test, as indicated by a [RequiresSql] annotation
     * (or annotations which are themselves annotated with [RequiresSql]), on the method defining the test. May be empty.
     */
    fun beforeTest(setupSql: List<String>)

    /**
     * Called once after a given test is executed.
     *
     * @param teardownSql The names of any SQL scripts to be run after running the test, as indicated by a [RequiresSql] annotation
     * (or annotations which are themselves annotated with [RequiresSql]), on the method defining the test. May be empty.
     */
    fun afterTest(teardownSql: List<String>)
}