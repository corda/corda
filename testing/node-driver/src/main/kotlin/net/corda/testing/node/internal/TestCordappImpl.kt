package net.corda.testing.node.internal

import net.corda.testing.node.TestCordapp
import java.nio.file.Path

data class TestCordappImpl(override val name: String,
                           override val version: String,
                           override val vendor: String,
                           override val title: String,
                           override val targetVersion: Int,
                           override val cordappVersion: String,
                           override val config: Map<String, Any>,
                           override val packages: Set<String>,
                           override val signJar: Boolean = false,
                           val keyStorePath: Path? = null,
                           val classes: Set<Class<*>>
                           ) : TestCordapp {

    override fun withName(name: String): TestCordappImpl = copy(name = name)

    override fun withVersion(version: String): TestCordappImpl = copy(version = version)

    override fun withVendor(vendor: String): TestCordappImpl = copy(vendor = vendor)

    override fun withTitle(title: String): TestCordappImpl = copy(title = title)

    override fun withTargetVersion(targetVersion: Int): TestCordappImpl = copy(targetVersion = targetVersion)

    override fun withCordappVersion(version: String): TestCordappImpl = copy(cordappVersion = version)

    override fun withConfig(config: Map<String, Any>): TestCordappImpl = copy(config = config)

    override fun signJar(keyStorePath: Path?): TestCordappImpl = copy(signJar = true, keyStorePath = keyStorePath)

    fun withClasses(vararg classes: Class<*>): TestCordappImpl {
        return copy(classes = classes.filter { clazz -> packages.none { clazz.name.startsWith("$it.") } }.toSet())
    }
}
