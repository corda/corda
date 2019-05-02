package net.corda.testing.internal.db

import org.junit.jupiter.api.extension.ExtendWith

/**
 * An annotation which is applied to test classes to indicate that they belong to a group of tests which require a common database
 * environment, which is initialized before any of the tests in any of the classes in that group are run, and cleaned up after all of them
 * have completed.
 *
 * @param group The name of the group of tests to which the annotated test belongs, or `"default"` if unstated.
 * @param defaultContextClassName The class name of the [TestDatabaseContext] which should be instantiated to manage the database
 * environment for these tests, if none is given in the system property `test.db.context./groupName/`. This defaults to the class name of
 * [NoOpTestDatabaseContext].
 */
@ExtendWith(DBRunnerExtension::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class RequiresDb(
        val group: String = "default",
        val defaultContextClassName: String = "net.corda.testing.internal.db.NoOpTestDatabaseContext")

/**
 * An annotation which is applied to test classes and methods to indicate that the corresponding test suite  / instance requires SQL scripts
 * to be run against its database as part of its setup / teardown.
 *
 * @param name The name of the SQL script to run. The same name will be used for setup and teardown: it is up to the [TestDatabaseContext] to
 * select the actual SQL script based on the context, e.g. `"specialSql"` may be translated to `"/groupName/-specialSql-setup.sql"` or to
 * `"/groupName/-specialSql-teardown.sql"` depending on which operation is being performed.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresSql(val name: String)