package net.corda.errorPageBuilder

import net.corda.common.logging.errorReporting.ErrorResource
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URLClassLoader
import java.util.*

class ErrorTableGenerator(private val resourceLocation: File,
                          private val locale: Locale) {

    companion object {
        private const val ERROR_CODE_HEADING = "codeHeading"
        private const val ALIASES_HEADING = "aliasesHeading"
        private const val DESCRIPTION_HEADING = "descriptionHeading"
        private const val TO_FIX_HEADING = "toFixHeading"
        private const val ERROR_HEADINGS_BUNDLE = "ErrorPageHeadings"
        private const val ERROR_INFO_RESOURCE = "ErrorInfo.properties"
    }

    private fun getHeading(heading: String) : String {
        val resource = ResourceBundle.getBundle(ERROR_HEADINGS_BUNDLE, locale)
        return resource.getString(heading)
    }

    private fun listResources() : Iterator<String> {
        return resourceLocation.walkTopDown().filter {
            it.name.matches("[^_]*\\.properties".toRegex()) && !it.name.matches(ERROR_INFO_RESOURCE.toRegex())
        }.map {
            it.nameWithoutExtension
        }.iterator()
    }

    private fun createLoader() : ClassLoader {
        val urls = resourceLocation.walkTopDown().map { it.toURI().toURL() }.asIterable().toList().toTypedArray()
        return URLClassLoader(urls)
    }

    private fun generateTable() : List<List<String>> {
        val table = mutableListOf<List<String>>()
        val loader = createLoader()
        for (resource in listResources()) {
            val errorResource = ErrorResource.fromLoader(resource, loader, locale)
            table.add(listOf(resource, errorResource.aliases, errorResource.shortDescription, errorResource.actionsToFix))
        }
        return table
    }

    private fun formatTable(tableData: List<List<String>>) : String {
        val headings = listOf(
                getHeading(ERROR_CODE_HEADING),
                getHeading(ALIASES_HEADING),
                getHeading(DESCRIPTION_HEADING),
                getHeading(TO_FIX_HEADING)
        )
        val underlines = headings.map { "-".repeat(it.length) }
        val fullTable = listOf(headings, underlines) + tableData
        return fullTable.joinToString(System.lineSeparator()) { it.joinToString(prefix = "| ", postfix = " |", separator = " | ") }
    }

    fun generateMarkdown() : String {
        if (!resourceLocation.exists()) throw IllegalArgumentException("Directory $resourceLocation does not exist.")
        val tableData = generateTable()
        return formatTable(tableData)
    }
}