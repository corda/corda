package net.corda.djvm.tools.cli

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassAndMemberVisitor
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member
import net.corda.djvm.source.ClassSource
import picocli.CommandLine.*
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPOutputStream

@Command(
        name = "generate",
        description = ["Generate and export whitelist from the class and member declarations provided in one or more " +
                "JARs."]
)
@Suppress("KDocMissingDocumentation")
class WhitelistGenerateCommand : CommandBase() {

    @Parameters(description = ["The paths of the JARs that the whitelist is to be generated from."])
    var paths: Array<Path> = emptyArray()

    @Option(
            names = ["-o", "--output"],
            description = ["The file to which the whitelist will be written. If not provided, STDOUT will be used."]
    )
    var output: Path? = null

    override fun validateArguments() = paths.isNotEmpty()

    override fun handleCommand(): Boolean {
        val entries = AnalysisConfiguration.createRoot().use { configuration ->
            val entries = mutableListOf<String>()
            val visitor = object : ClassAndMemberVisitor(configuration, null) {
                override fun visitClass(clazz: ClassRepresentation): ClassRepresentation {
                    entries.add(clazz.name)
                    return super.visitClass(clazz)
                }

                override fun visitMethod(clazz: ClassRepresentation, method: Member): Member {
                    visitMember(clazz, method)
                    return super.visitMethod(clazz, method)
                }

                override fun visitField(clazz: ClassRepresentation, field: Member): Member {
                    visitMember(clazz, field)
                    return super.visitField(clazz, field)
                }

                private fun visitMember(clazz: ClassRepresentation, member: Member) {
                    entries.add("${clazz.name}.${member.memberName}:${member.signature}")
                }
            }
            val context = AnalysisContext.fromConfiguration(configuration)
            for (path in paths) {
                ClassSource.fromPath(path).getStreamIterator().forEach {
                    visitor.analyze(it, context)
                }
            }
            entries
        }
        output?.also {
            Files.newOutputStream(it, StandardOpenOption.CREATE).use { out ->
                GZIPOutputStream(out).use { gzip ->
                    PrintStream(gzip).use { pout ->
                        pout.println("""
                            |java/.*
                            |javax/.*
                            |jdk/.*
                            |com/sun/.*
                            |sun/.*
                            |---
                            """.trimMargin().trim())
                        printEntries(pout, entries)
                    }
                }
            }
        } ?: printEntries(System.out, entries)
        return true
    }

    private fun printEntries(stream: PrintStream, entries: List<String>) {
        for (entry in entries.sorted().distinct()) {
            stream.println(entry)
        }
    }

}
