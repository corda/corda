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


package com.intel.sgx.dialogs;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.intel.sgx.handlers.TwoStepSignHandlerBase;

public class TwoStepSignStep1Dialog1 extends TwoStepSignDialogBase {

	final private TwoStepSignHandlerBase handler;

	public TwoStepSignStep1Dialog1(Shell parentShell, TwoStepSignHandlerBase handler) {
		super(parentShell);
		this.handler = handler;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1, false);
		composite.setLayout(gridLayout);

		enclaveFileField = addGroup(composite, "Unsigned Enclave Path:",
				"Select Enclave for which to generate the Hash.",
				"Enclave Path:", "Select Enclave", enclaveFileSelectionListener);

		hashFileField = addGroup(composite, "Generate Hash:",
				"Select Location to Output Hash File.", "Hash File Location:",
				"Select File Path", hashFileSelectionListener);

		configFileField = addGroup(composite, "Configuration File:",
				"Select Input Configuration XML File.  ",
				"Configuration File:", "Select Config",
				configFileSelectionListener);

		composite.pack(true);
		return composite;
	}

	@Override
	protected void okPressed() {
		handler.enclaveFile = enclaveFileField.getText();
		handler.hashFile = hashFileField.getText();
		handler.configFile = configFileField.getText();
		
		super.okPressed();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Two Step Enclave Sign - Generate Hash");
	}
	
	

}
