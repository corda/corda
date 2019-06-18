package net.corda.testing.internal.db

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@RequiresDb("groupA", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
annotation class GroupA

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@RequiresDb("groupB", "net.corda.testing.internal.db.AssertingTestDatabaseContext")
@RequiresSql("forClassGroupBTests")
annotation class GroupB

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@RequiresSql("specialSql1")
annotation class SpecialSql1

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@RequiresSql("specialSql2")
annotation class SpecialSql2

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@RequiresSql("forClassGroupATests")
annotation class GroupASql