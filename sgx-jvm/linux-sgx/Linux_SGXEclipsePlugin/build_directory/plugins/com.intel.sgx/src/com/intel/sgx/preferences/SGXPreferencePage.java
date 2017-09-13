///////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Intel Corporation.				 //
// 									 //
// All rights reserved. This program and the accompanying materials	 //
// are made available under the terms of the Eclipse Public License v1.0 //
// which accompanies this distribution, and is available at		 //
// http://www.eclipse.org/legal/epl-v10.html				 //
// 									 //
// Contributors:							 //
//     Intel Corporation - initial implementation and documentation	 //
///////////////////////////////////////////////////////////////////////////


package com.intel.sgx.preferences;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.intel.sgx.Activator;
import com.intel.sgx.SdkPathVariableProvider;

/**
 * This class represents a preference page that
 * is contributed to the Preferences dialog. By 
 * subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows
 * us to create a page that is small and knows how to 
 * save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They
 * are stored in the preference store that belongs to
 * the main plug-in class. That way, preferences can
 * be accessed directly via the preference store.
 */

public class SGXPreferencePage
	extends FieldEditorPreferencePage
	implements IWorkbenchPreferencePage {

	private SGXSdkDirectoryFieldEditor sgxSdkDirectoryEditor;
	
	public SGXPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Intel(R) SGX Preferences");
	}
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
		
	@Override
    protected void createFieldEditors() {
        sgxSdkDirectoryEditor = new SGXSdkDirectoryFieldEditor(PreferenceConstants.SDK_PATH,
        		"&Intel(R) SGX SDK Directory:", getFieldEditorParent());
        addField(sgxSdkDirectoryEditor);
    }

	/*
	 * Validates whether the path entered in the Intel(R) SGX SDK Preferences points to the Intel(R) SGX SDK or not.
	 */
    private static class SGXSdkDirectoryFieldEditor extends DirectoryFieldEditor {
        public SGXSdkDirectoryFieldEditor(String name, String labelText, Composite parent) {
            super(name, labelText, parent);
            setEmptyStringAllowed(true);
        }

        @Override
        protected boolean doCheckState() {
            if (!super.doCheckState()) {
                setErrorMessage("Intel(R) SGX Preferences: Not a Valid directory");
                return false;
            }

            String dirname = getTextControl().getText().trim();
            if (!dirname.isEmpty() && !SdkPathVariableProvider.isValidSGXSdkLocation(dirname)) {
                setErrorMessage("Intel(R) SGX SDK: Not a Valid SGX SDK directory");
                return false;
            }
            return true;
        }

        @Override
        public Text getTextControl(Composite parent) {
            setValidateStrategy(VALIDATE_ON_KEY_STROKE);
            return super.getTextControl(parent);
        }
    }

    @Override
	public void init(IWorkbench workbench) {
	}

    @Override
    public void dispose() {
        super.dispose();

        if (sgxSdkDirectoryEditor != null) {
        	sgxSdkDirectoryEditor.dispose();
        	sgxSdkDirectoryEditor = null;
        }
    }
}
