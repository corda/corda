package net.corda.buildtasks

class DetektChangedFiles {

    static def getChangedFiles() {
        String teamcityFile = Properties.getProperties()get("teamcity.build.changedFiles.file")
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
