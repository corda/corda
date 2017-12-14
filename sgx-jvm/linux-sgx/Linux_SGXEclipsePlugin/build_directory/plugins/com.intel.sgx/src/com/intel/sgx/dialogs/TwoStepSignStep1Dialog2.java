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

public class TwoStepSignStep1Dialog2 extends TwoStepSignDialogBase {

	final private String hashFile;

	public TwoStepSignStep1Dialog2(Shell parentShell, String hashFile) {
		super(parentShell);
		this.hashFile = hashFile;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1,false);
		composite.setLayout(gridLayout);
		
		addGroup1(composite);
		addGroup3(composite);
		
		
		return composite;
	}

	private void addGroup1(Composite composite) {
		final Group container = new Group(composite, SWT.None);
		container.setLayout(new GridLayout(3,false));
		GridData innergrid1 = new GridData(GridData.FILL_HORIZONTAL);
	innergrid1.horizontalSpan = 3;
		container.setLayoutData(innergrid1);
		container.setText("Hash File:");
		
		final Label messageLabel = new Label(container, SWT.NONE);
		messageLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel.setText("Hash File Generated at Location:");
		
		final Label messageLabel1 = new Label(container, SWT.NONE);
		messageLabel1.setText(hashFile);

		messageLabel1.setLayoutData(new GridData(GridData.BEGINNING));
	}

	private void addGroup3(Composite composite) {
		final Group container3 = new Group(composite, SWT.None);
		container3.setLayout(new GridLayout(3,false));
		GridData innergrid3 = new GridData(GridData.FILL_HORIZONTAL);
		innergrid3.horizontalSpan = 3;
		container3.setLayoutData(innergrid3);
		container3.setText("Generate Signed Enclave (Step-2):");
		
		final Label messageLabel4 = new Label(container3, SWT.NONE);
		messageLabel4.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel4.setText("To Generate Signed Enclave Now: Click OK");
		
		final Label messageLabel5 = new Label(container3, SWT.NONE);
		messageLabel5.setLayoutData(new GridData(GridData.CENTER, GridData.END, false, false, 3, 1));
		messageLabel5.setText("To Generate Signed Enclave Later: Click Cancel");
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Two Step Enclave Sign - Generate Hash");
	}
	
	
}
