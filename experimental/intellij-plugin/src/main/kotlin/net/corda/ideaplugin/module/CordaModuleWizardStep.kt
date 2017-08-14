package net.corda.ideaplugin.module

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.Version
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import java.awt.BorderLayout
import javax.swing.*

class CordaModuleWizardStep(private val config: CordaModuleConfiguration) : ModuleWizardStep() {
    private val sdkPanel = CordaModuleWizardPanel()

    override fun getComponent() = sdkPanel

    override fun updateDataModel() {
        config.cordaSdkVersion = sdkPanel.cordaSdkVersion
        config.language = sdkPanel.language!!
        config.appName = sdkPanel.appName
        config.packageName = "${sdkPanel.domain}.${sdkPanel.appName.decapitalize()}"
        config.group = sdkPanel.domain
        config.version = sdkPanel.version
        config.selectedTemplate = sdkPanel.templates
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        sdkPanel.cordaSdkVersion ?: throw ConfigurationException("Specify Corda SDK Version")
        return super.validate()
    }
}

class CordaModuleWizardPanel : JPanel(BorderLayout()) {
    val cordaSdkVersion: Version? get() = cordaSdkVersionComboBox.getSelectedCordaSdkVersion()
    val language: Language? get() = languageComboBox.selectedItem as? Language
    val appName: String get() = appNameTextField.text
    val domain: String get() = domainTextField.text
    val version: String get() = versionTextField.text
    val templates: List<CordaTemplate> get() = CordaTemplate.values().filter { templateList.isItemSelected(it) }

    private val cordaSdkVersionComboBox = CordaSdkVersionComboBox(Version(0, 12, 1), Version(0, 11, 1))
    private val languageComboBox = JComboBox(DefaultComboBoxModel(Language.values()))
    private val appNameTextField = JBTextField("MyCorDapp")
    private val domainTextField = JBTextField("com.myCompany")
    private val versionTextField = JBTextField("1.0.0")
    private val templateList = CheckBoxList<CordaTemplate>().apply {
        CordaTemplate.values().forEach {
            addItem(it, it.displayName, it == CordaTemplate.CORDAPP)
        }
        setCheckBoxListListener { i, _ ->
            val template = getItemAt(i) as? CordaTemplate
            template?.let { if (!it.optional) setItemSelected(it, true) }
        }
    }

    init {
        val myRoot = panel {
            border = BorderFactory.createEmptyBorder(4, 5, 0, 5)
            //row("Project SDK:") { projectSdkComboBox(CCFlags.growX, CCFlags.pushX) }
            row("Corda SDK Version:") { cordaSdkVersionComboBox(CCFlags.growX, CCFlags.pushX) }
            row("Language:") { languageComboBox(CCFlags.growX, CCFlags.pushX) }
            row(JLabel("CorDapp Name:"), separated = true) { appNameTextField(CCFlags.growX, CCFlags.pushX) }
            row("Company Domain:") { domainTextField(CCFlags.growX, CCFlags.pushX) }
            //TODO:  row("Package Name:") { packageName(CCFlags.growX, CCFlags.pushX) }
            row("Version:") { versionTextField(CCFlags.growX, CCFlags.pushX) }
        }

        val template = panel {
            row(separated = true) { (JLabel("Templates and Examples:"))(CCFlags.growX, CCFlags.pushX) }
            row { (JBScrollPane(templateList))(CCFlags.grow, CCFlags.push) }
        }
        add(myRoot, BorderLayout.NORTH)
        add(template, BorderLayout.CENTER)
        languageComboBox.selectedItem = Language.KOTLIN
    }
}

enum class Language(val displayName: String) {
    JAVA("Java"),
    KOTLIN("Kotlin");

    override fun toString(): String {
        return displayName
    }
}
