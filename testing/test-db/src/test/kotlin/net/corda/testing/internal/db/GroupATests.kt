package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@RequiresDb("groupA", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
@GroupASql
class GroupATests {

    @Test
    fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupA",
                "forClassGroupATests-setup", "specialSql1-setup", "specialSql1-teardown", "forClassGroupATests-teardown")
    }

    @Test
    fun noSpecialSqlRequired() {

    }

    @Test
    @SpecialSql1
    fun someSpecialSqlRequired() {

    }

}