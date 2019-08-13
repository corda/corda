package net.corda.tools.shell.base

// Note that this file MUST be in a sub-directory called "base" relative to the path
// given in the configuration code in InteractiveShell.

// Copy of the login.groovy file from 'shell' module with the welcome tailored for the standalone shell
welcome = """

Welcome to the Corda interactive shell.
Useful commands include 'help' to see what is available, and 'bye' to exit the shell.

"""

prompt = { ->
    return "${new Date()}>>> "
}
