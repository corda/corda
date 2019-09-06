package net.corda.buildtasks

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class DetektChangedFiles {
    private static final Logger logger = Logging.getLogger(DetektChangedFiles.class)

    static def getChangedFiles() {
        String teamcityFile = System.getProperty("system.teamcity.build.changedFiles.file")
        logger.debug("TC FILE!!!: " + teamcityFile)
        String[] fileList
//        if (!teamcityFile.isEmpty()) {
        String fileContents = new File(teamcityFile).text
        fileList = fileContents.trim().split("\n")
        for (int i = 0; i < fileList.length; i++) {
            int endIndex = fileList[i].indexOf(":CHANGED")
            fileList[i] = fileList[i].substring(0, endIndex)
        }
//        } else {
//            def stOut = new StringBuilder()
//            def sysError = new StringBuilder()
//            def proc = 'git diff --name-only'.execute()
//            proc.consumeProcessOutput(stOut, sysError)
//            fileList = stOut.toString().trim().split("\n")
//        }

        return fileList
    }
}
