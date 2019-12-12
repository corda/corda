package net.corda.tools.shell.base

// Note that this file MUST be in a sub-directory called "base" relative to the path
// given in the configuration code in InteractiveShell.

welcome = { ->
    """
        
        Welcome to the Corda interactive shell.
        You can see the available commands by typing 'help'.
        
    """.stripIndent()
}

prompt = { ->
    return "${new Date()}>>> "
}
