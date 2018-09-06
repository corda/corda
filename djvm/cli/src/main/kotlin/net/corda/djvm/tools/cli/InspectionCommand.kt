package net.corda.djvm.tools.cli

import net.corda.djvm.source.ClassSource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Files

@Command(
        name = "inspect",
        description = ["Inspect the transformations that are being applied to classes before they get loaded into " +
                "the sandbox."]
)
@Suppress("KDocMissingDocumentation")
class InspectionCommand : ClassCommand() {

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
            val sourceClass = createCodePath().resolve("${loadedClass.type.simpleName}.class")
            val originalClass = Files.createTempFile("sandbox-", ".java")
            val transformedClass = Files.createTempFile("sandbox-", ".java")

            printInfo("Class: ${loadedClass.name}")
            printVerbose(" - Size of the original byte code: ${Files.size(sourceClass)}")
            printVerbose(" - Size of the transformed byte code: ${loadedClass.byteCode.bytes.size}")
            printVerbose(" - Original class: $originalClass")
            printVerbose(" - Transformed class: $transformedClass")
            printInfo()

            // Generate byte code dump of the original class
            ProcessBuilder("javap", "-c", sourceClass.toString()).apply {
                redirectOutput(originalClass.toFile())
                environment().putAll(System.getenv())
                start().apply {
                    waitFor()
                    exitValue()
                }
            }

            // Generate byte code dump of the transformed class
            Files.createTempFile("sandbox-", ".class").apply {
                Files.write(this, loadedClass.byteCode.bytes)
                ProcessBuilder("javap", "-c", this.toString()).apply {
                    redirectOutput(transformedClass.toFile())
                    environment().putAll(System.getenv())
                    start().apply {
                        waitFor()
                        exitValue()
                    }
                }
                Files.delete(this)
            }

            // Generate and display the difference between the original and the transformed class
            ProcessBuilder(
                    "git", "diff", originalClass.toString(), transformedClass.toString()
            ).apply {
                inheritIO()
                environment().putAll(System.getenv())
                start().apply {
                    waitFor()
                    exitValue()
                }
            }
            printInfo()

            Files.deleteIfExists(originalClass)
            Files.deleteIfExists(transformedClass)
        }
    }

}
