/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.ideaplugin.module

object CordaTemplateProvider {
    fun getTemplateFiles(cordaTemplate: CordaTemplate, language: Language): List<TemplateFile> {
        return when (language) {
            Language.KOTLIN -> getKotlinTemplateFiles(cordaTemplate)
            Language.JAVA -> getJavaTemplateFiles(cordaTemplate)
        }
    }

    private fun getKotlinTemplateFiles(cordaTemplate: CordaTemplate): List<TemplateFile> {
        return when (cordaTemplate) {
            CordaTemplate.CORDAPP -> listOf(
                    TemplateFile("Main.kt", "", appendProjectName = false),
                    TemplateFile("Flow.kt", "/flow"),
                    TemplateFile("Contract.kt", "/contract"),
                    TemplateFile("State.kt", "/state"))
            CordaTemplate.WEBAPI -> listOf(
                    TemplateFile("Api.kt", "/api"),
                    TemplateFile("Plugin.kt", "/plugin"))
            CordaTemplate.RPC -> listOf(TemplateFile("ClientRPC.kt", "/client"))
        }
    }

    private fun getJavaTemplateFiles(cordaTemplate: CordaTemplate): List<TemplateFile> {
        // TODO: Provide java template.
        return when (cordaTemplate) {
            CordaTemplate.CORDAPP -> listOf()
            CordaTemplate.WEBAPI -> listOf()
            CordaTemplate.RPC -> listOf()
        }
    }
}

enum class CordaTemplate(val displayName: String, val optional: Boolean = true) {
    CORDAPP("CorDapp Template", optional = false),
    WEBAPI("Web API Template"),
    RPC("RPC Client Template")
}

data class TemplateFile(val fileName: String, val targetPath: String, val appendProjectName: Boolean = true)
