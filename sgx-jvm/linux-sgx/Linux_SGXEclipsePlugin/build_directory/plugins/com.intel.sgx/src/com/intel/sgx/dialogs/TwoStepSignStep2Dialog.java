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
import com.intel.sgx.handlers.TwoStepSignStep2;

public class TwoStepSignStep2Dialog extends TwoStepSignDialogBase{

	final private TwoStepSignHandlerBase handler;


	public TwoStepSignStep2Dialog(Shell parentShell, TwoStepSignHandlerBase handler) {
		super(parentShell);
		this.handler = handler;
	}

	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1,false);
		composite.setLayout(gridLayout);
		
		
		
		enclaveFileField= addGroup(composite, "Enclave:",
				"Select the unsigned enclave file",
				"Unsigned Enclave File:", "Select", enclaveFileSelectionListener);
		
		configFileField = addGroup(composite, "Configuration File:",
				"Select Input Configuration XML File.  ",
				"Configuration File:", "Select Config",
				configFileSelectionListener);

		hashFileField= addGroup(composite, "Hash:",
				"Select the Hash file obtained from previous step",
				"Hash File:", "Select", hashFileSelectionListener);

		externalSignPublicKeyFileField= addGroup(composite, "Public Key:",
				"Select the Public Key file obtained from external signing facility",
				"Public Key:", "Select", publicKeyLocationSelectionListener);
		
		externalSignedHashFileField = addGroup(composite, "Signature:",
				"Select the Signature file obtained from signing facility.", 
				"Signature:",
				"Select", externalSignedHashFileSelectionListener);

		outputSignedEnclaveFileField = addGroup(composite, "Signed Enclave:",
				"Select where to save the output Signed Enclave.", 
				"Signed Enclave:",
				"Select", outputSignedEnclaveListener);

		return composite;
	}


	@Override
	protected void okPressed() {
		handler.enclaveFile = enclaveFileField.getText();
		handler.hashFile = hashFileField.getText();
		handler.configFile = configFileField.getText();
		handler.externalSignPublicKeyFile = externalSignPublicKeyFileField.getText();
		handler.externallySignedHashFile = externalSignedHashFileField.getText(); 
		handler.outputSignedEnclaveFile = outputSignedEnclaveFileField.getText();

		super.okPressed();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Two Step Enclave Sign - Generate Signed Enclave");
	}
	
}
