package net.corda.buildtasks

class DetektChangedFiles {

    static def getChangedFiles() {
        def stOut = new StringBuilder()
        def sysError = new StringBuilder()
        def proc = 'git diff --name-only'.execute()
        proc.consumeProcessOutput(stOut, sysError)
        return stOut.toString().trim().split("\n")
    }
}
