package net.corda.tools.shell

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import org.crsh.command.BaseCommand
import org.crsh.shell.impl.command.CRaSHSession

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
open class InteractiveShellCommand : BaseCommand() {
    fun ops() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).rpcOps
    fun ansiProgressRenderer() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).ansiProgressRenderer
    fun objectMapper(classLoader: ClassLoader?): ObjectMapper {
        val om = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).yamlInputMapper
        if (classLoader != null) {
            om.typeFactory = TypeFactory.defaultInstance().withClassLoader(classLoader)
        }
        return om
    }

    fun isSsh() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).isSsh
}
