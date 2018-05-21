package net.corda.tools.shell.base

// Note that this file MUST be in a sub-directory called "base" relative to the path
// given in the configuration code in InteractiveShell.

welcome = """

Welcome to the Corda interactive shell.
Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

"""

prompt = { ->
    return "${new Date()}>>> "
}
