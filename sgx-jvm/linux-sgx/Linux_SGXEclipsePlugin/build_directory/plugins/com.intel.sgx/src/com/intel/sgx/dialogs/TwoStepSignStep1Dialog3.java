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

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.intel.sgx.handlers.TwoStepSignHandlerBase;

public class TwoStepSignStep1Dialog3 extends TwoStepSignDialogBase {
	
	final private TwoStepSignHandlerBase handler;

	public TwoStepSignStep1Dialog3(Shell parentShell, TwoStepSignHandlerBase handler) {
		super(parentShell);
		this.handler = handler;
	}

	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1, false);
		composite.setLayout(gridLayout);

		addInfoGroup(composite);

		externalSignPublicKeyFileField = addGroup(composite, "Public Key:",
				"Select the Public Key file obtained from signing facility",
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
	
	protected void addInfoGroup(Composite composite) {
		final Group container = new Group(composite, SWT.None);
		container.setLayout(new GridLayout(3,false));
		GridData innergrid1 = new GridData(GridData.FILL_HORIZONTAL);
		innergrid1.horizontalSpan = 3;
		container.setLayoutData(innergrid1);
		container.setText("Hash and Enclave:");

		
		addInfoKeyValue(container, "Enclave File:", handler.enclaveFile);
		addInfoKeyValue(container, "Config File:", handler.configFile);
		addInfoKeyValue(container, "Hash File:", handler.hashFile);
	}

	private void addInfoKeyValue(final Group container, String key,
			String value) {
		final Label messageLabel2 = new Label(container, SWT.NONE);
		messageLabel2.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel2.setText(key);
		
		final Label messageLabel3 = new Label(container, SWT.NONE);
		messageLabel3.setText(value);
		messageLabel3.setLayoutData(new GridData(GridData.BEGINNING));
	}

	@Override
	protected void okPressed() {
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
