package net.corda.sandbox.tools.cli

import net.corda.sandbox.source.ClassSource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Files

@Command(
        name = "show",
        description = ["Show the transformed version of a class as it is prepared for execution in the deterministic " +
                "sandbox."]
)
@Suppress("KDocMissingDocumentation")
class ShowCommand : ClassCommand() {

    override val filters: Array<String>
        get() = classes

    @Parameters(description = ["The partial or fully qualified names of the Java classes to inspect."])
    var classes: Array<String> = emptyArray()

    override fun processClasses(classes: List<Class<*>>) {
        val sources = classes.map { ClassSource.fromClassName(it.name) }
        val (_, messages) = executor.validate(*sources.toTypedArray())

        if (messages.isNotEmpty()) {
            for (message in messages.sorted()) {
                printInfo(" - $message")
            }
            printInfo()
        }

        for (classSource in sources) {
            val loadedClass = executor.load(classSource)
            printInfo("Class: ${loadedClass.name}")
            printVerbose(" - Byte code size: ${loadedClass.byteCode.bytes.size}")
            printVerbose(" - Has been modified: ${loadedClass.byteCode.isModified}")
            printInfo()

            Files.createTempFile("sandbox-", ".class").apply {
                Files.write(this, loadedClass.byteCode.bytes)
                ProcessBuilder("javap", "-c", this.toString()).apply {
                    inheritIO()
                    environment().putAll(System.getenv())
                    start().apply {
                        waitFor()
                        exitValue()
                    }
                }
                Files.delete(this)
            }
            printInfo()
        }
    }

}
