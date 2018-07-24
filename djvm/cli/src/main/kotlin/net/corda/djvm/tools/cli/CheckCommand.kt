package net.corda.djvm.tools.cli

import net.corda.djvm.source.ClassSource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
        name = "check",
        description = ["Statically validate that a class or set of classes (and their dependencies) do not violate any " +
                "constraints posed by the deterministic sandbox environment."]
)
@Suppress("KDocMissingDocumentation")
class CheckCommand : ClassCommand() {

    override val filters: Array<String>
        get() = classes

    @Parameters(description = ["The partial or fully qualified names of the Java classes to analyse and validate."])
    var classes: Array<String> = emptyArray()

    override fun printSuccess(classes: List<Class<*>>) {
        for (clazz in classes.sortedBy { it.name }) {
            printVerbose("Class ${clazz.name} validated")
        }
        printVerbose()
    }

    override fun processClasses(classes: List<Class<*>>) {
        val sources = classes.map { ClassSource.fromClassName(it.name) }
        val summary = executor.validate(*sources.toTypedArray())
        printMessages(summary.messages)
    }

}
