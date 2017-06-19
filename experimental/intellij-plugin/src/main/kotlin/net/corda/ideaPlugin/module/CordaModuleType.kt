package net.corda.ideaPlugin.module

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.util.IconLoader

class CordaModuleType : ModuleType<CordaModuleBuilder>(ID) {
    companion object {
        val ID = "CORDA_MODULE"
        val CORDA_ICON = IconLoader.getIcon("/images/corda-icon.png")
        val instance = CordaModuleType()
    }

    override fun createModuleBuilder() = CordaModuleBuilder()
    override fun getNodeIcon(p0: Boolean) = CORDA_ICON
    override fun getBigIcon() = CORDA_ICON
    override fun getName() = "CorDapp"
    override fun getDescription() = "Corda DLT Platform Application"
}

