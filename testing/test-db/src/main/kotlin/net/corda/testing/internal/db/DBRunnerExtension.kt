package net.corda.testing.internal.db

import org.junit.jupiter.api.extension.*

/***
 * A JUnit 5 [Extension] which invokes a [TestDatabaseContext] to manage database state across three scopes:
 *
 * * Test run (defined across multiple classes)
 * * Test suite (defined in a single class)
 * * Test instance (defined in a single method)
 *
 * A test class will not ordinarily register this extension directly: instead, it is registered for any class having the [RequiresDb]
 * annotation, which it consults to discover which group of tests the test class belongs to (`"default"`, if not stated).
 *
 * The class of the [TestDatabaseContext] used is selected by a system property, `test.db.context./groupName/`, where `groupName` is the
 * name of the group of tests to which the test using this extension belongs. If this system property is not set, the class name defaults
 * to the [RequiresDb.defaultContextClassName] stated in the annotation, which in turn defaults to the class of [NoOpTestDatabaseContext].
 *
 * When [BeforeAllCallback.beforeAll] is called prior to executing any test methods in a given class, the [ExtensionContext.Store] of the
 * root extension context is used to look up the [TestDatabaseContext] for the class's declared `groupName`, creating and initialising it
 * if it does not already exist. This ensures that a [TestDatabaseContext] is created exactly once during each test run for every named
 * group of tests using this extension. This context will be closed with a call to [ExtensionContext.Store.CloseableResource.close] once
 * the test run completes, tearing down the database state created at the beginning.
 *
 * For each test suite and test instance, this extension looks at the corresponding class or method to see if it is annotated with
 * [RequiresSql], indicating that further SQL setup/teardown is required around the current scope. If it is, then appropriate calls are made
 * to [TestDatabaseContext.beforeClass], [TestDatabaseContext.beforeTest], [TestDatabaseContext.afterTest] and
 * [TestDatabaseContext.afterClass], passing through the name of the SQL script to be run. (Note that the same name is used for setup and
 * teardown, and it is up to the [TestDatabaseContext] to map this to the appropriate SQL script for each case).
 */
class DBRunnerExtension : Extension, BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    override fun beforeAll(context: ExtensionContext?) {
        // Always obtain/create a database context
        val dbContext = getDatabaseContext(context) ?: return
        val testSql = context?.testClass?.orElse(null)?.getAnnotation(RequiresSql::class.java)?.name ?: return
        dbContext.beforeClass(testSql)
    }

    override fun afterAll(context: ExtensionContext?) {
        val testSql = context?.testClass?.orElse(null)?.getAnnotation(RequiresSql::class.java)?.name ?: return
        getDatabaseContext(context)?.afterClass(testSql)
    }

    override fun beforeEach(context: ExtensionContext?) {
        val testSql = context?.testMethod?.orElse(null)?.getAnnotation(RequiresSql::class.java)?.name ?: return
        getDatabaseContext(context)?.beforeTest(testSql)
    }

    override fun afterEach(context: ExtensionContext?) {
        val testSql = context?.testMethod?.orElse(null)?.getAnnotation(RequiresSql::class.java)?.name ?: return
        getDatabaseContext(context)?.afterTest(testSql)
    }

    private fun getDatabaseContext(context: ExtensionContext?): TestDatabaseContext? {
        val rootContext = context?.root ?: return null

        val testClass = context.testClass.orElse(null) ?: return null
        val annotation = testClass.getAnnotation(RequiresDb::class.java) ?:
            throw IllegalStateException("Test run with DBRunnerExtension is not annotated with @RequiresDb")
        val groupName = annotation.group
        val defaultContextClassName = annotation.defaultContextClassName

        val store = rootContext.getStore(ExtensionContext.Namespace.create(DBRunnerExtension::class.java.simpleName, groupName))
        return store.getOrComputeIfAbsent(
                TestDatabaseContext::class.java.simpleName,
                { createDatabaseContext(groupName, defaultContextClassName) },
                TestDatabaseContext::class.java)
    }

    private fun createDatabaseContext(groupName: String, defaultContextClassName: String): TestDatabaseContext {
        val propertyKey = "test.db.context.$groupName"
        val className = System.getProperty(propertyKey) ?: defaultContextClassName

        val ctx = Class.forName(className).newInstance() as TestDatabaseContext
        ctx.initialize(groupName)
        return ctx
    }
}