package net.corda.node.internal

import com.google.inject.AbstractModule
import com.google.inject.Provides
import joptsimple.OptionException
import net.corda.node.ArgsParser
import net.corda.node.CmdLineOptions
import kotlin.system.exitProcess

interface NodeArgumentParser {
    val argsParser: ArgsParser
    val cmdLineOptions: CmdLineOptions
}

class NodeStartupArgumentsModule(val arguments: Array<String>): AbstractModule() {

    @Provides
    fun providerNodeArgumentParser() : NodeArgumentParser = NodeArgumentParserImpl(arguments)

    @Provides
    fun provideCmdLineOptions(nodeArgumentParser: NodeArgumentParser): CmdLineOptions = nodeArgumentParser.cmdLineOptions

    @Provides
    fun provideArgsParser(nodeArgumentParser: NodeArgumentParser): ArgsParser = nodeArgumentParser.argsParser

    override fun configure() {  }
}

private class NodeArgumentParserImpl(val args: Array<String>): NodeArgumentParser {
   override val argsParser: ArgsParser
    override val cmdLineOptions: CmdLineOptions

    init {
        val argsParser = ArgsParser()
        val cmdlineOptions = try {
            argsParser.parse(*args)
        } catch (ex: OptionException) {
            println("Invalid command line arguments: ${ex.message}")
            argsParser.printHelp(System.out)
            exitProcess(1)
        }
        this.argsParser = argsParser
        this.cmdLineOptions = cmdlineOptions
    }
}

