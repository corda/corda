package net.corda.behave.file

import java.io.File

class LogSource(
        private val directory: File,
        filePattern: String? = ".*\\.log",
        private val filePatternUsedForExclusion: Boolean = false
) {

    private val fileRegex = Regex(filePattern ?: ".*")

    data class MatchedLogContent(
            val filename: File,
            val contents: String
    )

    fun find(pattern: String? = null): List<MatchedLogContent> {
        val regex = if (pattern != null) {
            Regex(pattern)
        } else {
            null
        }
        val logFiles = directory.listFiles({ file ->
            (!filePatternUsedForExclusion && file.name.matches(fileRegex)) ||
                    (filePatternUsedForExclusion && !file.name.matches(fileRegex))
        })
        val result = mutableListOf<MatchedLogContent>()
        for (file in logFiles) {
            val contents = file.readText()
            if (regex != null) {
                result.addAll(regex.findAll(contents).map { match ->
                    MatchedLogContent(file, match.value)
                })
            } else {
                result.add(MatchedLogContent(file, contents))
            }
        }
        return result
    }

}