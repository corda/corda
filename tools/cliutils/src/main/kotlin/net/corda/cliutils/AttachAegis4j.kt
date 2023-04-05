package net.corda.cliutils

import com.sun.tools.attach.VirtualMachine
import net.gredler.aegis4j.AegisAgent
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object AttachAegis4j {
    private fun toBytes(clazz: Class<*>): ByteArray? {
        val path = clazz.name.replace('.', '/') + ".class"
        val stream = clazz.classLoader.getResourceAsStream(path)
        return stream.readBytes()
    }

    private fun createAgentJar(): String {
        val clazz: Class<*> = AegisAgent::class.java
        val jar = Files.createTempFile("aegis4j-", ".jar")
        jar.toFile().deleteOnExit()
        val manifest = Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue("Main-Class", clazz.name)
        manifest.mainAttributes.putValue("Agent-Class", clazz.name)
        manifest.mainAttributes.putValue("Premain-Class", clazz.name)
        manifest.mainAttributes.putValue("Can-Redefine-Classes", "true")
        manifest.mainAttributes.putValue("Can-Retransform-Classes", "true")
        manifest.mainAttributes.putValue("Can-Set-Native-Method-Prefix", "false")
        Files.newOutputStream(jar).use { os ->
            JarOutputStream(os, manifest).use { jos ->
                val entry = JarEntry(clazz.name.replace('.', '/') + ".class")
                entry.time = System.currentTimeMillis()
                jos.putNextEntry(entry)
                jos.write(toBytes(clazz))
                jos.closeEntry()
            }
        }
        return jar.toAbsolutePath().toString()
    }

    init {
        val pid = ManagementFactory.getRuntimeMXBean().getName().substringBefore('@')
        var jvm = VirtualMachine.attach(pid)
        jvm.loadAgent(createAgentJar(), "resource=mods.properties")
        jvm.detach()
    }
}