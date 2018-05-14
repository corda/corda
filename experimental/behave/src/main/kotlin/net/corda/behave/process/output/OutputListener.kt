package net.corda.behave.process.output

interface OutputListener {

    fun onNewLine(line: String)

    fun onEndOfStream()
}