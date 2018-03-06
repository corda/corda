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

import com.intellij.openapi.util.Version
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JList

class CordaSdkVersionComboBox(vararg val sdkVersions: Version) : JComboBox<Version>() {
    init {
        renderer = object : ColoredListCellRenderer<Any?>() {
            override fun customizeCellRenderer(list: JList<*>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
                if (value is Version) {
                    append(value.toString())
                } else {
                    append("Select Corda SDK Version", SimpleTextAttributes.ERROR_ATTRIBUTES)
                }
            }
        }
        updateSdkList(null, true)
    }

    fun updateSdkList(sdkVersionToSelect: Version?, selectAnySdk: Boolean) {
        model = DefaultComboBoxModel(arrayOf(null, *sdkVersions))
        selectedItem = if (selectAnySdk) sdkVersions.firstOrNull() else sdkVersionToSelect
    }

    fun getSelectedCordaSdkVersion() = selectedItem as? Version
}