package net.corda.testing.db

import org.junit.jupiter.api.Test

@RequiresDb("groupA", "net.corda.testing.db.AssertingTestDatabaseContext")
@RequiresSql("forClassGroupATests")
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
    @RequiresSql("specialSql1")
    fun someSpecialSqlRequired() {

    }

}