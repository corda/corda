package net.corda.behave.file

import net.corda.core.internal.list
import net.corda.core.internal.readText
import java.nio.file.Path
import kotlin.streams.toList

class LogSource(
        private val directory: Path,
        filePattern: String? = ".*\\.log",
        private val filePatternUsedForExclusion: Boolean = false
) {

    private val fileRegex = Regex(filePattern ?: ".*")

    data class MatchedLogContent(
            val filename: Path,
            val contents: String
    )

    fun find(pattern: String? = null): List<MatchedLogContent> {
        val regex = if (pattern != null) {
            Regex(pattern)
        } else {
            null
        }
        val logFiles = directory.list {
            it.filter {
                (!filePatternUsedForExclusion && it.fileName.toString().matches(fileRegex)) ||
                (filePatternUsedForExclusion && !it.fileName.toString().matches(fileRegex))
            }.toList()
        }
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