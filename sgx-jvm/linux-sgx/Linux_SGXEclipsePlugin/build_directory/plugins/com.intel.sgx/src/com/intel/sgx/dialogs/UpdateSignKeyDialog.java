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

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
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

import com.intel.sgx.handlers.UpdateSigningKey;

public class UpdateSignKeyDialog extends SGXDialogBase {

	private Shell shell;
	public Text sourceKeyFileField;
	public Text destinationKeyFileField;
	public static boolean regenerate = false;
	
	private final SelectionListener destinationKeyFileSelectionListener = new SelectionListener() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			String result = destinationKeyFileField.getText();
			FileDialog dialog = new FileDialog(shell, SWT.OPEN);
			dialog.setFilterPath(getCurrentProjectPath().toOSString());
			dialog.setFilterExtensions(new String [] {"*.pem", "*"});
			result = dialog.open();
			destinationKeyFileField.setText(result);
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
		}		
	};
	
	private final SelectionListener sourceKeyFileSelectionListener = new SelectionListener() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			String result = sourceKeyFileField.getText();
			FileDialog dialog = new FileDialog(shell, SWT.OPEN);
			dialog.setFilterExtensions(new String [] {"*.pem", "*"});
			dialog.setFilterPath(getCurrentProjectPath().toOSString());
			result = dialog.open();
			sourceKeyFileField.setText(result);
		}
		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
		}		
	};
	final private UpdateSigningKey handler;
	
	public UpdateSignKeyDialog(Shell parentShell, UpdateSigningKey handler) {
		super(parentShell);
		this.shell = parentShell;
		this.handler = handler;
		setShellStyle(SWT.RESIZE | SWT.TITLE);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		final GridLayout gridLayout = new GridLayout(1,false);
		composite.setLayout(gridLayout);
		
		destinationKeyFileField = addGroup(composite, "Enclave Signing Key:",
				"Select the Signing Key to be Updated or Generated.",
				"Enclave Signing Key:", "Select", destinationKeyFileSelectionListener);

		sourceKeyFileField = addGroup(composite, "Import:",
				"To import your own Signing Key use the Import Signing Key option.",
				"Import Signing Key:", "Import Key", sourceKeyFileSelectionListener);

		addGroup2(composite);
		
		return composite;
	}

	protected void addGroup2(Composite composite) {
		final Group container2 = new Group(composite, SWT.None);
		container2.setLayout(new GridLayout(3,false));
		GridData innergrid2 = new GridData(GridData.FILL_HORIZONTAL);
		innergrid2.horizontalSpan = 3;
		container2.setLayoutData(innergrid2);
		container2.setText("Generate:");
		
		final Label messageLabel3 = new Label(container2, SWT.NONE);
		messageLabel3.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 3, 1));
		messageLabel3.setText("To Generate a new Signing Key use the Generate Signing Key option.");
		
		Label warningLabel2 = new Label(container2,SWT.FILL | SWT.WRAP);
		warningLabel2.setText("Generate a new Signing Key:");
		warningLabel2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label dummy2 = new Label(container2,0);
		dummy2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		final Button updateSigningKey = new Button(container2, SWT.PUSH);
		updateSigningKey.setText("Generate Key");
		GridData buttonGridData2 = new GridData(GridData.END);
		buttonGridData2.horizontalAlignment = SWT.RIGHT;
		buttonGridData2.horizontalSpan = 1;
		buttonGridData2.minimumWidth = 120;
		updateSigningKey.setLayoutData(buttonGridData2);
		updateSigningKey.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if( !destinationKeyFileField.getText().isEmpty())
				{
					regenerate = true;
					UpdateSignKeyDialog.this.setReturnCode(InputDialog.OK);
					okPressed();
					UpdateSignKeyDialog.this.close();
				}
				else
					JOptionPane.showMessageDialog(null, "Enclave Signing Key field is not provided.", "Error",
							JOptionPane.ERROR_MESSAGE);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}		
		});
	}

	
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Import or (Re)Generate Enclave Signing Key");
	}

	@Override
	protected void okPressed() {
		
		handler.sourceKeyFile = sourceKeyFileField.getText();
		handler.destinationKeyFile = destinationKeyFileField.getText();
		if((!sourceKeyFileField.getText().isEmpty() &&  !destinationKeyFileField.getText().isEmpty() && 
				(new File(sourceKeyFileField.getText())).isFile())
				|| regenerate == true )
		{
			System.out.println("regenerate = " + regenerate);
			super.okPressed();
		}
		else
		{
			if(sourceKeyFileField.getText().isEmpty() && destinationKeyFileField.getText().isEmpty())
				JOptionPane.showMessageDialog(null, "Enclave Signing Key and Import Singing Key are not provided.", "Error",
					JOptionPane.ERROR_MESSAGE);
			else
			{
				if(sourceKeyFileField.getText().isEmpty())
					JOptionPane.showMessageDialog(null, "Import Singing Key is not provided.", "Error",
							JOptionPane.ERROR_MESSAGE);
				else
				if(!(new File(sourceKeyFileField.getText())).isFile())
					JOptionPane.showMessageDialog(null, "Invalid Import Singing Key.", "Error",
							JOptionPane.ERROR_MESSAGE);
				
				if(destinationKeyFileField.getText().isEmpty())
					JOptionPane.showMessageDialog(null, "Enclave Signing Key is not provided.", "Error",
							JOptionPane.ERROR_MESSAGE);
			}
		}
			
	}

	
	
}
