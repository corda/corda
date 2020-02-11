package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@GroupA
class GroupAMoreTests {

    @Test()
    fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupA",
                "specialSql1-setup", "specialSql2-setup", "specialSql2-teardown", "specialSql1-teardown")
    }

    @Test()
    @SpecialSql1
    @SpecialSql2
    fun moreSpecialSqlRequired() {
    }
}