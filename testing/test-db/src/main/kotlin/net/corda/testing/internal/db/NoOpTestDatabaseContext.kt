package net.corda.testing.internal.db

/**
 * An implementation of [TestDatabaseContext] which does nothing.
 */
class NoOpTestDatabaseContext : TestDatabaseContext {

    override fun initialize(groupName: String) {}

    override fun beforeClass(setupSql: String) {}

    override fun afterClass(teardownSql: String) {}

    override fun beforeTest(setupSql: String) {}

    override fun afterTest(teardownSql: String) {}

    override fun close() {}

}