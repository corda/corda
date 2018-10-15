package net.corda.testing.node.internal

import net.corda.testing.node.TestCordapp

data class TestCordappImpl(override val name: String,
                           override val version: String,
                           override val vendor: String,
                           override val title: String,
                           override val targetVersion: Int,
                           override val packages: Set<String>,
                           val classes: Set<Class<*>>) : TestCordapp {

    override fun withName(name: String): TestCordappImpl = copy(name = name)

    override fun withVersion(version: String): TestCordappImpl = copy(version = version)

    override fun withVendor(vendor: String): TestCordappImpl = copy(vendor = vendor)

    override fun withTitle(title: String): TestCordappImpl = copy(title = title)

    override fun withTargetVersion(targetVersion: Int): TestCordappImpl = copy(targetVersion = targetVersion)

    fun withClasses(vararg classes: Class<*>): TestCordappImpl {
        return copy(classes = classes.filter { clazz -> packages.none { clazz.name.startsWith("$it.") } }.toSet())
    }
}
