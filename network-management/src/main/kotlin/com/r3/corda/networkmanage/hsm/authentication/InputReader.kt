/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
            requireNotNull(kotlin.io.readLine()) { "Password required" }
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