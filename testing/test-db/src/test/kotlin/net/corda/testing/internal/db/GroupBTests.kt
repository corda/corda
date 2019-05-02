package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@RequiresDb("groupB", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
@RequiresSql("forClassGroupBTests")
class GroupBTests {

    @Test
    fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupB",
                "forClassGroupBTests-setup", "specialSql1-setup", "specialSql1-teardown", "forClassGroupBTests-teardown")
    }

    @Test
    fun noSpecialSqlRequired() {

    }

    @Test
    @RequiresSql("specialSql1")
    fun someSpecialSqlRequired() {

    }

}