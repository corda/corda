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

import java.io.File;

import javax.swing.JOptionPane;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.intel.sgx.handlers.AddUntrustedModule;

public class AddUntrustedModuleDialog extends Dialog {

	private Text fileNameField,makeFilePathField;
	private Shell shell;
	private AddUntrustedModule addHandler;
	private boolean generateApp = false;
	
	public AddUntrustedModuleDialog(Shell shell, AddUntrustedModule addHandler) {
		super(shell);
		this.addHandler = addHandler;
		this.shell = shell;
		//setShellStyle(SWT.RESIZE | SWT.TITLE);
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
		
		final Label messageLabel = new Label(container, SWT.NONE);
		messageLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel.setText("Enter the path to the Enclave Descriptor file (*.edl) of the enclave to host.");
		
		
		final Label fileNameLabel = new Label(container, SWT.NONE);
		fileNameLabel.setText("Filename:");
		fileNameLabel.setLayoutData(new GridData(GridData.BEGINNING,GridData.CENTER, false, false));
		
		
		fileNameField = new Text(container,SWT.SINGLE | SWT.BORDER);
		GridData textGridData1 = new GridData(GridData.FILL_HORIZONTAL);
		textGridData1.minimumWidth = 400;
		textGridData1.grabExcessHorizontalSpace = true;
		fileNameField.setLayoutData(textGridData1);

		final Button browseButton = new Button(container, SWT.PUSH);
		browseButton.setText("Browse");
		GridData buttonGridData1 = new GridData(GridData.END);
		buttonGridData1.horizontalAlignment = SWT.RIGHT;
		buttonGridData1.horizontalSpan = 1;
		buttonGridData1.minimumWidth = 120;
		browseButton.setLayoutData(buttonGridData1);
		browseButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				String result = null;
                shell = new Shell();
				FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				dialog.setFilterExtensions(new String [] {"*.edl"});
				dialog.setFilterPath("");
				result = dialog.open();
				fileNameField.setText(result);
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}		
		});
		
		return composite;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Add Intel(R) SGX Untrusted Module.");
	}
	
	@Override
	protected
	void okPressed(){
		addHandler.edlFilename = fileNameField.getText();
		if(!fileNameField.getText().isEmpty()) 
				if((new File(fileNameField.getText())).isFile())
					super.okPressed();
				else
					JOptionPane.showMessageDialog(null, "EDL file does not exist.", "Error",
							JOptionPane.ERROR_MESSAGE);
	}
	
	@Override
	protected Point getInitialSize(){
		return new Point(675,200);
	}
	
	public String getFileName() {
		return fileNameField.getText();
	}
}
