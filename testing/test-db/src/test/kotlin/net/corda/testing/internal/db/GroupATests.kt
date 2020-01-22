package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@RequiresDb("groupA", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
@GroupASql
class GroupATests {

    @Test(timeout=300_000)
	fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupA",
                "forClassGroupATests-setup", "specialSql1-setup", "specialSql1-teardown", "forClassGroupATests-teardown")
    }

    @Test(timeout=300_000)
	fun noSpecialSqlRequired() {
    }

    @Test(timeout=300_000)
@SpecialSql1
    fun someSpecialSqlRequired() {
    }
}