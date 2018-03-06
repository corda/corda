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

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.util.IconLoader

class CordaModuleType : ModuleType<CordaModuleBuilder>(ID) {
    companion object {
        val ID = "CORDA_MODULE"
        val CORDA_ICON = IconLoader.getIcon("/images/corda-module-icon.png")
        val CORDA_ICON_BIG = IconLoader.getIcon("/images/corda-module-icon@2x.png")
        val instance = CordaModuleType()
    }

    override fun createModuleBuilder() = CordaModuleBuilder()
    override fun getNodeIcon(p0: Boolean) = CORDA_ICON
    override fun getBigIcon() = CORDA_ICON_BIG
    override fun getName() = "CorDapp"
    override fun getDescription() = "Corda DLT Platform Application"
}

