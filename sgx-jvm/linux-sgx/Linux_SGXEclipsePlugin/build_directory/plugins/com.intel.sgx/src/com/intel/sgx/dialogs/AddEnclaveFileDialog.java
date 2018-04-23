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

import javax.swing.JOptionPane;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.intel.sgx.handlers.AddEnclave;

public class AddEnclaveFileDialog extends SGXDialogBase {

	private Text fileNameField;
	private AddEnclave addHandler;
	private boolean generateApp = true;
	
	public AddEnclaveFileDialog(Shell shell, AddEnclave addHandler) {
		super(shell);
		this.addHandler = addHandler;
		this.shell = shell;
		// setShellStyle(SWT.RESIZE | SWT.TITLE);
	}
	
	public boolean generateApp()
	{
		return generateApp;
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1,false);
		composite.setLayout(gridLayout);
		
		final Group container = new Group(composite, SWT.NONE);
		container.setLayout(new GridLayout(3,false));
		GridData innergrid1 = new GridData(GridData.FILL_HORIZONTAL);
		innergrid1.horizontalSpan = 3;
		container.setLayoutData(innergrid1);
		
		addLabel(container, "Enter the name of the Enclave. Make sure the name is unique within the hosting application.");
			
		final Label fileNameLabel = new Label(container, SWT.NONE);
		fileNameLabel.setText("Enclave name:");
		fileNameLabel.setLayoutData(new GridData(GridData.BEGINNING,GridData.CENTER, false, false));

		fileNameField = new Text(container,SWT.SINGLE | SWT.BORDER);
		GridData textGridData1 = new GridData(GridData.FILL_HORIZONTAL);
		textGridData1.minimumWidth = 400;
		textGridData1.grabExcessHorizontalSpace = true;
		fileNameField.setLayoutData(textGridData1);

        Button generateUntrustedApp = new Button(container, SWT.CHECK);
        generateUntrustedApp.setText("Generate sample untrusted application?");
        generateUntrustedApp.setLayoutData(new GridData(GridData.BEGINNING, GridData.END, false, false, 3, 1));
        generateUntrustedApp.setSelection(true);
        
        generateUntrustedApp.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent event) {
                Button btn = (Button) event.getSource();
                generateApp = btn.getSelection();
            }
        });
        
        container.layout();
        composite.layout();
		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Add New Intel(R) SGX Enclave Dialog");
		newShell.layout();
	}
	
	@Override
	protected
	void okPressed() {
		addHandler.edlFilename = fileNameField.getText();
		if(!fileNameField.getText().isEmpty() 
				){
				if(Character.isDigit(fileNameField.getText().charAt(0)))
				{
				JOptionPane.showMessageDialog(null, "Enclave names starting with digits are not allowed.", "Error",
						JOptionPane.ERROR_MESSAGE);
				}
				else
				super.okPressed();
		}
	}
	
	@Override
	protected Point getInitialSize(){
		return new Point(675,200);
	}
	
	public String getFileName() {
		return fileNameField.getText();
	}
}
