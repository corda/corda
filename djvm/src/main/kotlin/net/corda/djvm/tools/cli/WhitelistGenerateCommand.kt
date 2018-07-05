package net.corda.djvm.tools.cli

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassAndMemberVisitor
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member
import net.corda.djvm.source.ClassSource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path

@Command(
        name = "generate",
        description = ["Generate and export whitelist from the class and member declarations provided in one or more " +
                "JARs."]
)
@Suppress("KDocMissingDocumentation")
class WhitelistGenerateCommand : CommandBase() {

    @Parameters(description = ["The paths of the JARs that the whitelist is to be generated from."])
    var paths: Array<Path> = emptyArray()

    override fun validateArguments() = paths.isNotEmpty()

    override fun handleCommand(): Boolean {
        val entries = mutableListOf<String>()
        val visitor = object : ClassAndMemberVisitor() {
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
        val context = AnalysisContext.fromConfiguration(AnalysisConfiguration(), emptyList())
        for (path in paths) {
            ClassSource.fromPath(path).getStreamIterator().forEach {
                visitor.analyze(it, context)
            }
        }
        printEntries(System.out, entries)
        return true
    }

    private fun printEntries(stream: PrintStream, entries: List<String>) {
        for (entry in entries.sorted().distinct()) {
            stream.println(entry)
        }
    }

}
