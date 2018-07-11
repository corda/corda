package net.corda.bootstrapper.nodes

import java.io.File

open class FoundNode(open val configFile: File,
                     open val baseDirectory: File = configFile.parentFile,
                     val name: String = configFile.parentFile.name.toLowerCase().replace(net.corda.bootstrapper.Constants.ALPHA_NUMERIC_ONLY_REGEX, "")) {


    operator fun component1(): File {
        return baseDirectory;
    }

    operator fun component2(): File {
        return configFile;
    }

    operator fun component3(): String {
        return name;
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FoundNode

        if (configFile != other.configFile) return false
        if (baseDirectory != other.baseDirectory) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = configFile.hashCode()
        result = 31 * result + baseDirectory.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String {
        return "FoundNode(name='$name', configFile=$configFile, baseDirectory=$baseDirectory)"
    }

    fun toCopiedNode(copiedNodeConfig: File, copiedNodeDir: File): CopiedNode {
        return CopiedNode(this.configFile, this.baseDirectory, copiedNodeConfig, copiedNodeDir)
    }


}


