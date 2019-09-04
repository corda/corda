package net.corda.testing.internal.db

/**
 * An implementation of [TestDatabaseContext] which does nothing.
 */
class NoOpTestDatabaseContext : TestDatabaseContext {

    override fun initialize(groupName: String) {}

    override fun beforeClass(setupSql: List<String>) {}

    override fun afterClass(teardownSql: List<String>) {}

    override fun beforeTest(setupSql: List<String>) {}

    override fun afterTest(teardownSql: List<String>) {}

    override fun close() {}
}