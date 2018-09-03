package net.corda.ideaplugin.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.service.project.wizard.GradleModuleBuilder.appendToFile
import org.jetbrains.plugins.gradle.service.project.wizard.GradleModuleBuilder.setupGradleSettingsFile
import java.io.File
import java.io.IOException

class CordaModuleBuilder : JavaModuleBuilder(), ModuleBuilderListener {
    private val config = CordaModuleConfiguration()

    init {
        addListener(this)
    }

    override fun getBuilderId() = "corda"

    override fun moduleCreated(module: Module) {
        val contentEntryPath = contentEntryPath
        if (contentEntryPath.isNullOrEmpty()) return
        val contentRootDir = File(contentEntryPath)
        FileUtilRt.createDirectory(contentRootDir)
        val fileSystem = LocalFileSystem.getInstance()
        val modelContentRootDir = fileSystem.refreshAndFindFileByIoFile(contentRootDir) ?: return

        val packagePath = "src/main/kotlin/${config.packageName.replace(".", "/")}"
        setupGradleSettingsFile(modelContentRootDir.path, modelContentRootDir, module.project.name, module.name, true)
        setupFile(modelContentRootDir, "", "build.gradle", "build.gradle")

        config.selectedTemplate.flatMap { CordaTemplateProvider.getTemplateFiles(it, config.language) }.forEach {
            setupFile(modelContentRootDir, "$packagePath/${it.targetPath}", "${if (it.appendProjectName) config.appName else ""}${it.fileName}", it.fileName)
        }
    }

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable?) = CordaModuleWizardStep(config)

    override fun modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep? {
        if (settingsStep is ProjectSettingsStep) {
            val projectSettingsStep = settingsStep
            val moduleNameField = settingsStep.moduleNameField
            moduleNameField.text = config.appName
            projectSettingsStep.setModuleName(config.packageName)
            projectSettingsStep.bindModuleSettings()
        }
        return super.modifySettingsStep(settingsStep)
    }

    override fun getPresentableName() = "CorDapp"

    override fun getGroupName() = "Corda"

    override fun getModuleType(): ModuleType<*> = CordaModuleType.instance

    private fun setupFile(modelContentRootDir: VirtualFile, path: String, fileName: String, templateName: String): VirtualFile? {
        val file = getOrCreateExternalProjectConfigFile("${modelContentRootDir.path}/$path", fileName) ?: return null
        saveFile(file, templateName, config.toMap())
        return file
    }

    private fun getOrCreateExternalProjectConfigFile(parent: String, fileName: String): VirtualFile? {
        val file = File(parent, fileName)
        FileUtilRt.createIfNotExists(file)
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    }

    private fun saveFile(file: VirtualFile, templateName: String, templateAttributes: Map<String, String?>?) {
        val manager = FileTemplateManager.getDefaultInstance()
        val template = manager.getInternalTemplate(templateName)
        try {
            appendToFile(file, if (templateAttributes != null) template.getText(templateAttributes) else template.text)
        } catch (e: IOException) {
            throw ConfigurationException(e.message, e.stackTrace.toString())
        }
    }
}

data class CordaModuleConfiguration(var cordaSdkVersion: Version? = null,
                                    var group: String = "",
                                    var appName: String = "",
                                    var packageName: String = "",
                                    var version: String = "",
                                    var language: Language = Language.KOTLIN,
                                    var selectedTemplate: List<CordaTemplate> = emptyList()) {

    fun toMap(): Map<String, String> {
        return mapOf("CORDA_SDK_VERSION" to cordaSdkVersion.toString(),
                "PROJECT_NAME" to appName,
                "PROJECT_NAME_DECAPITALIZE" to appName.decapitalize(),
                "PACKAGE_NAME" to packageName,
                "VERSION" to version,
                "GROUP" to group,
                "LANGUAGE" to language.displayName)
    }
}
