package net.corda.sandbox.tools.cli

import net.corda.sandbox.analysis.AnalysisConfiguration
import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.analysis.ClassAndMemberVisitor
import net.corda.sandbox.references.Class
import net.corda.sandbox.references.Member
import net.corda.sandbox.source.ClassSource
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
            override fun visitClass(clazz: Class): Class {
                entries.add(clazz.name)
                return super.visitClass(clazz)
            }

            override fun visitMethod(clazz: Class, method: Member): Member {
                visitMember(clazz, method)
                return super.visitMethod(clazz, method)
            }

            override fun visitField(clazz: Class, field: Member): Member {
                visitMember(clazz, field)
                return super.visitField(clazz, field)
            }

            private fun visitMember(clazz: Class, member: Member) {
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
