package net.corda.sandbox.tools.cli

import net.corda.sandbox.execution.SandboxedRunnable
import net.corda.sandbox.source.ClassSource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
        name = "run",
        description = ["Execute runnable in sandbox."],
        showDefaultValues = true
)
@Suppress("KDocMissingDocumentation")
class RunCommand : ClassCommand() {

    override val filters: Array<String>
        get() = classes

    @Parameters(description = ["The partial or fully qualified names of the Java classes to run."])
    var classes: Array<String> = emptyArray()

    override fun processClasses(classes: List<Class<*>>) {
        val interfaceName = SandboxedRunnable::class.java.simpleName
        for (clazz in classes) {
            if (!clazz.interfaces.any { it.simpleName == interfaceName }) {
                printError("Class is not an instance of $interfaceName; ${clazz.name}")
                return
            }
            printInfo("Running class ${clazz.name}...")
            executor.run(ClassSource.fromClassName(clazz.name), Any()).apply {
                printResult(result)
                printCosts(costs)
            }
        }
    }

}