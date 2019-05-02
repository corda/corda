package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@RequiresDb("groupA", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
class GroupAMoreTests {

    @Test
    fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupA",
                "specialSql2-setup", "specialSql2-teardown")
    }

    @Test
    @RequiresSql("specialSql2")
    fun moreSpecialSqlRequired() {

    }

}