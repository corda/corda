package net.corda.testing.internal.db

import org.junit.jupiter.api.Test

@GroupB
class GroupBTests {

    @Test()
fun setExpectations() {
        AssertingTestDatabaseContext.addExpectations("groupB",
                "forClassGroupBTests-setup", "specialSql1-setup", "specialSql1-teardown", "forClassGroupBTests-teardown")
    }

    @Test()
fun noSpecialSqlRequired() {
    }

    @Test()
@SpecialSql1
    fun someSpecialSqlRequired() {
    }
}