package com.r3.corda.networkmanage.hsm.authentication

/**
 * User input reader interface
 */
interface InputReader {
    /**
     * Reads a single line from user's input.
     */
    fun readLine(): String?

    /**
     * Reads a single line from user's input. The characters from the input are masked while being entered.
     * @param format message string displayed before user's input
     */
    fun readPassword(format: String): String
}

class ConsoleInputReader : InputReader {
    private val console = System.console()

    /** Read password from console, do a readLine instead if console is null (e.g. when debugging in IDE). */
    override fun readPassword(format: String): String {
        return if (console != null) {
            String(console.readPassword(format))
        } else {
            print(format)
            kotlin.io.readLine() ?: throw IllegalArgumentException("Password required")
        }
    }

    /** Read console line */
    override fun readLine(): String? {
        return if (console == null) {
            kotlin.io.readLine()
        } else {
            console.readLine()
        }
    }
}